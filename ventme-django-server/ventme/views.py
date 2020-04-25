from django.shortcuts import render, redirect, get_object_or_404

# Create your views here.
from django.template import loader, RequestContext
from django.http import HttpResponse, JsonResponse, FileResponse
from .models import Ventilator,SystemState
from django.contrib.messages.views import SuccessMessageMixin
from django.contrib.auth.mixins import LoginRequiredMixin
from django.contrib.auth.mixins import PermissionRequiredMixin, AccessMixin,UserPassesTestMixin
from django.views.generic.edit import CreateView, DeleteView, UpdateView,FormView
from django.urls import reverse, reverse_lazy
from django.contrib import messages
from django.conf import settings
from django.views.decorators.csrf import csrf_exempt
from django.utils import timezone

import logging

import csv
import string
import os
import shutil
from datetime import datetime,timedelta,date
import random
import pylatexenc
from pylatexenc.latex2text import LatexNodes2Text
from  .data_handler import *

log_vent = logging.getLogger('ventme')

#----------------------------------------------------------------------------
# utils


def random_string(stringLength=16):
    letters = string.ascii_lowercase
    return ''.join(random.choice(letters) for i in range(stringLength))


def get_or_none(model, *args, **kwargs):
    try:
        return model.objects.get(*args, **kwargs)
    except model.DoesNotExist:
        return None

def get_sys_state():
    s, created = SystemState.objects.get_or_create(pk = 1)
    return s

def get_user_rollno( u ):
    if( u.ldap_user ):
        rollno = u.ldap_user.attrs['employeeNumber']
        assert( len(rollno) > 0 )
        return rollno[0].upper()
    else:
        # test situation where we do not care of ldap authenticaiton
        u.username

def who_auth(request):
    u = request.user
    if u.is_anonymous:
        return None
    return "logged"

#----------------------------------------------------------------------------
# Global ram storage
# -- I hope no concurrency issues
# -- turn the following into classes

display_data = dict()

def reset_data( vid ):
    global display_data
    if vid in display_data:
        display_data[vid].reset()
    else:
        display_data[vid] = Ventilator_data()

def get_display_data(vid):
    global display_data
    return display_data[vid].get_display()

def put_packet( vid, pressure, airflow, volume, sample_rate ):
    global display_data
    return display_data[vid].put_packet(pressure, airflow, volume, sample_rate)
        
#----------------------------------------------------------------------------
# VIEWS

def index(request):
    p = who_auth(request)
    if p == None:
        return redirect( reverse("logout") )
    return redirect( reverse("all") )

def is_active( vent ):
    if ( vent == None ):
        log_vent.error( 'Attack!' )
        return False
    if not vent.is_registered:
        log_vent.error( 'Unregistered device contacting: '+str(vent.id) )
        return False
    return True

def is_error(vent):
    if vent.rr_error or vent.peep_error or vent.pmax_error or vent.oxygen_error or vent.ie_ratio_error:
        return True
    return False

def reset_vent( vent ):
    vent.packet_count = 0
    vent.sample_rate = 0
    vent.set_oxygen = 0
    vent.set_peep = 0
    vent.set_rr = 0
    vent.set_tidal_vol = 0
    vent.set_ie_ratio = None
    vent.oxygen = 0
    vent.rr_error = False
    vent.peep_error = False
    vent.pmax_error = False
    vent.oxygen_error = False
    vent.ie_ratio_error = False
    reset_data(vent.id)
    
def timeout_deregister( vent ):
    past = timezone.now()-timedelta( seconds = 10 )
    if vent.is_registered :
        if past > vent.last_contact :
            log_vent.error( 'Unregistered due to time gap: '+str(vent.id) )
            vent.is_registered = False
            vent.save()
            return True
    return False

# noone need to clll it?
# better soultion is not to display things that are too old
#
def timeout_delete( vent ):
    past = timezone.now()-timedelta( hours = 24 )
    if past > vent.last_contact :
        log_vent.error( 'Device deleted due to time gap: '+str(vent.id) )
        vent.delete()
        return True
    return False

def dump_data( vent, data_line ):
    if vent == None:
        return
    path = vent.data.name
    try:
        with open(path, "a+") as f:
            f.write( data_line + "\n" )
            f.close()
    except IOError as exc:
        log_vent.error( 'Failed to open : '+ path )
    
@csrf_exempt
def register(request):
    if request.POST == None:
        return HttpResponse( 'BadFormat' )
    # print(request.POST)

    # check if all the fields are there
    try:
        n       = request.POST['name']
        loc     = request.POST['location']
        patient = request.POST['patient']
        version = request.POST['protocol_version']
    except Exception as e:
        log_vent.error( 'Bad post received : '+ str(received.POST) )
        return HttpResponse( 'BadFormat' )

    vent, created = Ventilator.objects.get_or_create( name= n )

    now = timezone.now()
    if not created:
        # check if it is time to register
        timeout_deregister( vent )
        dump_data( vent, "Register at: "+str(now) )
    else:
        path = "".join([settings.MEDIA_ROOT, '/files-', vent.name, '.txt'])
        vent.data.name = path
        dump_data( vent,"Ventilator:"+vent.name+"\nRegister at: "+str(now) )
        # put ventilator name in the file
            
    if vent.is_registered:
        return HttpResponse( 'Already' )

    # reset/initialize the registration
    vent.registration_key = random_string()
    vent.patient = patient
    vent.is_registered = True
    vent.last_contact = now
    reset_vent(vent)
    vent.location = loc
    vent.save()
    return HttpResponse( str(vent.id) + ' ' + str(vent.registration_key) )

@csrf_exempt
def data( request, vid ) :
    if request.POST == None:
        return HttpResponse( 'BadFormat' )
    try:
        # print(request.POST)
        key = request.POST['reg_key']
        vent = get_or_none(Ventilator, id=vid)

        # check if device if device active and connection is valid
        if ( not is_active( vent ) ) or vent.registration_key != key :
            log_vent.error( 'Bad post received : ' + vid )
            return HttpResponse( 'BadFormat' )
            
        if timeout_deregister(vent):
            return HttpResponse( 'Unregistered' )

        packet_count = int(request.POST[ 'packet_count' ])
        if packet_count != (vent.packet_count + 1) % 32768:
            log_vent.error( 'Unregistered due to packet drop: '+str(vid) )
            vent.packet_count = packet_count
            vent.save()
            return HttpResponse( 'Dropped' )

        vent.packet_count = packet_count        
        vent.sample_rate  = int(request.POST[ 'sample_rate'  ])
        vent.num_samples  = int(request.POST[ 'num_samples'  ])

        vent.rr_error       = (request.POST[ 'rr_error'      ] == 'True')
        vent.peep_error     = (request.POST[ 'peep_error'    ] == 'True')
        vent.pmax_error     = (request.POST[ 'peep_error'    ] == 'True')
        vent.oxygen_error   = (request.POST[ 'oxygen_error'  ] == 'True')
        vent.ie_ratio_error = (request.POST[ 'ie_ratio_error'] == 'True')

        vent.set_oxygen   = int(request.POST[ 'set_oxygen'   ])
        vent.set_peep     = int(request.POST[ 'set_peep'     ])
        vent.set_rr       = int(request.POST[ 'set_rr'       ])
        vent.set_tidal_vol= int(request.POST[ 'set_tidal_vol'])
        vent.set_ie_ratio = request.POST[ 'set_ie_ratio' ]

        vent.oxygen       = int(request.POST[ 'oxygen'       ])
        # process ints
        pressure     = request.POST[ 'pressure'     ]
        airflow      = request.POST[ 'airflow'      ]
        tidal_volume = request.POST[ 'tidal_volume'    ]
        pressure = [float(x) for x in pressure.split(',')]
        airflow = [float(x) for x in airflow.split(',')]
        volume = [float(x) for x in tidal_volume.split(',')]

    except Exception as e:
        log_vent.error( 'Bad post received : '+ str(e) + vid )
        return HttpResponse( 'BadFormat' )

    put_packet( vent.id, pressure, airflow, volume, vent.sample_rate )       
    dump_data( vent, str(request.POST) )
    vent.last_contact = timezone.now()
    vent.save()
    return HttpResponse( 'Success' )    

def all_status(request):
    vents = Ventilator.objects.all()
    sys = get_sys_state()
    context = RequestContext(request)

    # check if anything to clear
    for vent in vents:
        timeout_deregister( vent )
    
    context.push( {'vents': vents } )
    return render( request, 'ventme/all.html', context.flatten() )


# only authorized users can reach to the following views

def out_of_range( val, set_val ):
    if val:
        return (abs(val-set_val)/set_val) > 0.1
    else:
        return False

def plot_data( request, vid ):
    u = who_auth(request)
    if u == None:
        return JsonResponse( {} )    

    vent = get_or_none(Ventilator, pk=vid)

    # check if device if the device is active
    if ( not is_active( vent ) ) or timeout_deregister(vent):
        return JsonResponse( {} )

    pressure, airflow, tidal, rr, ie_ratio,peep,pmax =get_display_data(vent.id)

    # check if the values are in range, if not raise alarm
    rr_error = vent.rr_error or out_of_range( rr, vent.set_rr )
    peep_error = vent.peep_error or out_of_range( peep, vent.set_peep )
    pmax_error = vent.pmax_error or (pmax > 60) # <---- TODO: check this val?
    o2_error = vent.oxygen_error or out_of_range(vent.oxygen,vent.set_oxygen )
    ie_error = vent.ie_ratio_error or ( ie_ratio != vent.set_ie_ratio )
    
    return JsonResponse( data={ 'rr' : rr,
                                'peep' : peep,
                                'pmax' : pmax,
                                'oxygen' : vent.oxygen,
                                'ieRatio' : ie_ratio,

                                'setRR'  : vent.set_rr,
                                'setPEEP' : vent.set_peep,
                                'setOxygen' : vent.set_oxygen,
                                'setIERatio' : vent.set_ie_ratio,

                                'rrError' : rr_error,
                                'peepError' : peep_error,
                                'pmaxError' : pmax_error,
                                'oxygenError' : o2_error,
                                'ieRatioError' : ie_error,

                                'pressureData': pressure,
                                'airflowData': airflow,
                                'tidalVolData': tidal, } )

def vent( request, vid ) :
    u = who_auth(request)
    if u == None:
        return HttpResponse( 'Incorrect login!' )    
    vent = get_or_none(Ventilator, pk=vid)

    # check if device if the device is active
    if ( not is_active( vent ) ) or timeout_deregister(vent):
        return HttpResponse( 'Unregistered' )

    # check if there was a time
    if timeout_deregister( vent ):
        return HttpResponse( 'Unregistered' )
    
    context = RequestContext(request)
    context.push( { 'vent': vent, 'num_samples' : num_display_samples } )
    return render( request, 'ventme/vent.html', context.flatten() )
    
