from django.shortcuts import render, redirect, get_object_or_404

# Create your views here.
from django.template import loader, RequestContext
from django.http import HttpResponse
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

counter = 0

#----------------------------------------------------------------------------
# VIEWS

def index(request):
    p = who_auth(request)
    if p == None:
        return redirect( reverse("logout") )
    return redirect( reverse("all") )

def is_active( vent ):
    if ( vent == None ):
        log_vent.error( 'Attack on: '+str(vent.id) )
        return False
    if not vent.is_registered:
        log_vent.error( 'Unregistered device contacting: '+str(vent.id) )
        return False
    return True

def reset_vent( vent ):
    vent.packet_count = 0
    vent.sample_rate = 0
    vent.set_oxygen = 0
    vent.set_peep = 0
    vent.set_rr = 0
    vent.set_tidal_vol = 0
    vent.set_ie_ratio = None
    vent.oxygen = 0
    is_error = False
        
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
        with open(path, "w+b") as f:
            f.write( data_line )
            f.close()
            f.write('input')
    except IOError as exc:
        log_vent.error( 'Failed to open : '+ path )
    
@csrf_exempt
def register(request):
    if request.POST == None:
        return HttpResponse( 'Unregistered' )        
    print(request.POST)
    n = request.POST['name']
    vent, created = Ventilator.objects.get_or_create( name= n )

    now = timezone.now()
    if not created:
        # check if it is time to register
        timeout_deregister( vent )
        dump_data( vent, "Register at: "+str(now) )
    else:
        path = join(settings.MEDIA_ROOT, 'files-', vent.name, '.txt')
        vent.data.name = path
        dump_data(vent,"Ventilator:"+vent.name+"\n Register at: "+str(now))
        # put ventilator name in the file
            
    if vent.is_registered:
        return HttpResponse( 'Already registered! Request ignored.' )

    # reset/initialize the registration
    vent.registration_key = random_string()
    vent.is_registered = True
    vent.last_contact = now
    reset_vent(vent)
    vent.location = request.POST['location']
    vent.save()
    return HttpResponse( str(vent.id) + ' ' + str(vent.registration_key) )

@csrf_exempt
def data( request, vid ) :
    if request.POST == None:
        return HttpResponse( 'Unregistered' )

    key = request.POST['reg_key']
    vent = get_or_none(Ventilator, pk=vid)

    # check if device if device active and connection is valid
    if ( not is_active( vent ) ) or vent.registration_key != key or timeout_deregister(vent):
        return HttpResponse( 'Unregistered' )

    #load data
    packet_count = request.POST[ 'packet_count' ]
    if int(packet_count) != (vent.packet_count + 1) % 32768:
        log_vent.error( 'Unregistered due to packet drop: '+str(p.id) )
        vent.is_registered = False
        vent.save()
        return HttpResponse( 'Unregistered' )

    vent.packet_count = packet_count
        
    # reading value
    vent.sample_rate  = int(request.POST[ 'sample_rate'  ])
    vent.num_samples  = int(request.POST[ 'num_samples'  ])
    error             = request.POST[ 'error'        ]

    vent.set_oxygen   = int(request.POST[ 'set_oxygen'   ])
    vent.set_peep     = int(request.POST[ 'set_peep'     ])
    vent.set_rr       = int(request.POST[ 'set_rr'       ])
    vent.set_tidal_vol= int(request.POST[ 'set_tidal_vol'])
    vent.set_ie_ratio = int(request.POST[ 'set_ie_ratio' ])

    oxygen       = int(request.POST[ 'oxygen'       ])
    # process ints
    pressure     = request.POST[ 'pressure'     ]
    airflow      = request.POST[ 'airflow'      ]
    tidal_volume = request.POST[ 'tidal_vol'    ]
        
    vent.last_contact = timezone.now()
    vent.save()
    return HttpResponse( 'Registered' )    

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

def plot_data( request, vid ):
    u = who_auth(request)
    if u == None:
        return JsonResponse( {} )    
    vent = get_or_none(Ventilator, pk=vid)

    # check if device if the device is active
    if ( not is_active( vent ) ) or timeout_deregister(vent):
        return JsonResponse( {} )
    
    return JsonResponse( response_data )

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
    context.push( {'vent': vent, 'data': data[vent.id] } )
    return render( request, 'ventme/display.html', context.flatten() )
    
