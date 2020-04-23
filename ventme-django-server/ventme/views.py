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

num_display_samples = 2000
num_maxmin_samples = 8
counters = dict()
pressureData = dict()
pressureLowPassData = dict()
diffPressureData = dict()
airflowData = dict()
volumeData = dict()
maxminCounter = dict()
maxminData = dict()

# derived data
rrs = dict()
peeps = dict()
ieratios = dict()

def get_display_data(vid):
    pData   = pressureData[vid]
    aData   = airflowData[vid]
    #tData   = volumeData[vid]
    #tData = pressureLowPassData[vid]
    tData = diffPressureData[vid]
    rr = rrs[vid]
    ieratio = ieratios[vid]
    peep = peeps[vid]
    return pData,aData,tData,rr,ieratio,peep

def compute_rr_ie( mmData, sample_rate ):
    # minima points are stored as negative numbers
    # and maxima points are stored as positive numbers
    lambdas = []
    ratios = []
    last_inhale = None
    last_exhale = None
    isMin = True
    if( mmData[0] < 0 ):
       isMin = False
    for i in range(1,num_maxmin_samples):
        assert( isMin == (mmData[i] < 0) )
        diff = mmData[i]+mmData[i-1]        
        if isMin:
            diff = -diff
        diff = diff % num_display_samples
        if isMin:
            last_exhale = diff
        else:
            last_inhale = diff
        if last_inhale != None and last_exhale != None and isMin:
            lambdas.append(last_inhale+last_exhale)
            ratios.append( last_inhale/last_exhale )
        isMin = not isMin
    avg_lambdas = sum(lambdas) / len(lambdas)
    avg_ratios = sum(ratios) / len(ratios)
    flipped = False
    if avg_ratios < 1:
        avg_ratios = 1/avg_ratios
        flipped = True
    ratio_str = None
    for i in range(1,4):
        if  i*0.8 < avg_ratios and avg_ratios < i*1.2:
            ratio_str = str(i)
    if ratio_str:
        if flipped :
            ratio_str = ratio_str + ':1'
        else:
            ratio_str = '1:' + ratio_str
    else:
        ratio_str = "-:-"
    return int(60*sample_rate/avg_lambdas),ratio_str

        
def put_packet( vid, pressure, airflow, volume, sample_rate ):
    lp = 0.98
    global counters, pressureData, airflowData, volumeData
    global pressureLowPassData

    counter = counters[vid]
    pData   = pressureData[vid]
    lpData  = pressureLowPassData[vid]
    dData  =  diffPressureData[vid]
    aData   = airflowData[vid]
    tData   = volumeData[vid]
    mmData  = maxminData[vid]
    mmCnt = maxminCounter[vid]
    rr = None
    ie = None
    peep = None
    
    pctr = (counter-1)% num_display_samples
    for idx in range( 0,len(pressure) ):
        pData[counter] = pressure[idx]
        aData[counter] = airflow[idx]
        tData[counter] = volume[idx]

        # lowpass, differentiate, and zero-crossing
        lpData[counter]=lp*lpData[pctr]+(1-lp)*pressure[idx]
        #lpData[counter]=lp*lpData[pctr]+(1-lp)*airflow[idx]
        dData[counter]=lpData[counter]-lpData[pctr]
        if( dData[counter] >= 0 and dData[pctr] < 0 ):
            # found a maxmia
            mmData[mmCnt] = counter + 1
            if mmCnt == num_maxmin_samples -1:
                rr,ie = compute_rr_ie( mmData, sample_rate )
            mmCnt = (mmCnt + 1) % num_maxmin_samples
        if( dData[counter] < 0 and dData[pctr] >= 0 ):
            # found a minima
            mmData[mmCnt] = -(counter + 1)
            if mmCnt == num_maxmin_samples -1:
                rr, ie = compute_rr_ie( mmData, sample_rate )
            mmCnt = (mmCnt + 1) % num_maxmin_samples
            
        pctr = counter
        counter = (counter + 1) % num_display_samples

    if counter >= num_display_samples - len(pressure) :
        peep = min(pData)
        
    pData[counter] = None
    aData[counter] = None
    tData[counter] = None
    lpData[counter] = None
    dData[counter] = None
    counters[vid] = counter
    maxminCounter[vid] = mmCnt
    if rr: rrs[vid] = rr
    if ie: ieratios[vid] = ie
    if peep: peeps[vid] = peep
    
def initialize_data( vid ):
    global counters, pressureData, airflowData, volumeData
    global pressureLowPassData
    counters[vid] = 0
    pressureData[vid] = [0]*num_display_samples
    pressureLowPassData[vid] = [0]*num_display_samples
    diffPressureData[vid] = [0]*num_display_samples
    airflowData[vid]  = [0]*num_display_samples
    volumeData[vid]   = [0]*num_display_samples
    rrs[vid] = None
    peeps[vid] = None
    ieratios[vid] = None
    maxminCounter[vid] = 0
    maxminData[vid] = [0]*num_maxmin_samples
    
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
    if vent.rr_error or vent.peep_error or vent.oxygen_error or vent.ie_ratio_error:
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
    vent.oxygen_error = False
    vent.ie_ratio_error = False
    initialize_data(vent.id)
    
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
    try:
        n = request.POST['name']
        loc = request.POST['location']
        vent, created = Ventilator.objects.get_or_create( name= n )
    except Exception as e:
        log_vent.error( 'Bad post received : '+ str(received.POST) )
        return HttpResponse( 'BadFormat' )

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

def plot_data( request, vid ):
    u = who_auth(request)
    if u == None:
        return JsonResponse( {} )    
    vent = get_or_none(Ventilator, pk=vid)

    # check if device if the device is active
    if ( not is_active( vent ) ) or timeout_deregister(vent):
        return JsonResponse( {} )

    # pressure_data = []
    # airflow_data = []
    # tidal_vol_data = []

    # for i in range(0,num_display_samples):
    #     pressure_data.append( random.randint(0,600)/100 )
    #     airflow_data.append( random.randint(0,600)/100 )
    #     tidal_vol_data.append( random.randint(0,300) )

    pressure_data, airflow_data, tidal_vol_data, rr, ie_ratio, peep = get_display_data(vent.id)

    # derived data
    # if rr == None: rr = "--"
    # if ie_ratio == None: ie_ratio = "-:-"    
    # peep = peep

    # data from objects
    # oxygen = vent.oxygen
    # set_rr = vent.set_rr
    # set_peep = vent.set_peep
    # set_oxygen = vent.set_oxygen
    # set_ie_ratio = vent.set_ie_ratio
    # rr_error = vent.rr_error 
    # peep_error = vent.peep_error
    # oxygen_error = vent.oxygen_error
    # ie_ratio_error = vent.ie_ratio_error
    
    
    return JsonResponse( data={ 'rr' : rr,
                                'peep' : peep,
                                'oxygen' : vent.oxygen,
                                'ieRatio' : ie_ratio,

                                'setRR'  : vent.set_rr,
                                'setPEEP' : vent.set_peep,
                                'setOxygen' : vent.set_oxygen,
                                'setIERatio' : vent.set_ie_ratio,

                                'rrError' : vent.rr_error,
                                'peepError' : vent.peep_error,
                                'oxygenError' : vent.oxygen_error,
                                'ieRatioError' : vent.ie_ratio_error,

                                'pressureData': pressure_data,
                                'airflowData': airflow_data,
                                'tidalVolData': tidal_vol_data, } )

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
    
