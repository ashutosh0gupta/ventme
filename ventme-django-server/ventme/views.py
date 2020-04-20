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

import logging

import csv
import os
import shutil
import datetime
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

#----------------------------------------------------------------------------
# VIEWS

def index(request):
    p = who_auth(request)
    if p == None:
        return redirect( reverse("logout") )
    return redirect( reverse("all") )
    
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


def register(request):
    if request.POST:
        n = reqt.POST['name']
        vent, created = StudentInfo.objects.get_or_create( name= n )
        if vent.is_registered:
            return HttpResponse( 'Already' )
        vent.registration_key = random_string()
        vent.is_registered = True
        vent.last_contact = datetime.now()
        if created:
            reset_vent(vent)
        vent.save()
        return HttpResponse( str(vent.id) + ' ' + str(vent.registration_key) )
    else:
        return HttpResponse( 'Unregistered' )

def data( request, vid ) :
    if request.POST == None:
        return HttpResponse( 'Unregistered' )

    key = request.POST['reg_key']
    vent = get_or_none(Ventilator, pk=vid)

    # check if device if device active 
    if ( vent == None or vent.registration_key != key ):
        log_vent.error( 'Attack on: '+str(vid) )
        return HttpResponse( 'Unregistered' )
    if not vent.is_registered:
        log_vent.error( 'Unregistered device contacting: '+str(vent.id) )
        return HttpResponse( 'Unregistered' )

    # check if there was a time
    past = datetime.now()-timedelta( seconds = 10 )
    if past > vent.last_contact:
        log_vent.error( 'Unregistered due to time gap: '+str(vent.id) )
        vent.is_registered = False
        vent.save()
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
    error        = request.POST[ 'error'        ]

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
        
    vent.last_contact = datetime.now()
    vent.save()
    return HttpResponse( 'Registered' )    

def all_status(request):
    vents = Ventilator.objects()
    sys = get_sys_state()
    context = RequestContext(request)
    context.push( {'vents': vents } )
    return render( request, 'ventme/all.html', context.flatten() )

def plot_data( request, vid ):
    u = who_auth(request)
    if u == None:
        return JsonResponse( {} )
    
    vent = get_or_none(Ventilator, pk=vid)
    if ( vent == None ):
        log_vent.error( 'Attack on: '+str(vid) )
        return HttpResponse( 'Unregistered' )
    if not vent.is_registered:
        log_vent.error( 'Unregistered device contacting: '+str(vent.id) )
        return HttpResponse( 'Unregistered' )

    # check if there was a time
    past = datetime.now()-timedelta( seconds = 10 )
    if past > vent.last_contact:
        log_vent.error( 'Unregistered due to time gap: '+str(vent.id) )
        vent.is_registered = False
        vent.save()
        return HttpResponse( 'Unregistered' )
    
    return JsonResponse( response_data )

def vent( request, vid ) :
    u = who_auth(request)
    if u == None:
        return HttpResponse( 'Incorrect login!' )
    
    vent = get_or_none(Ventilator, pk=vid)
    if ( vent == None ):
        log_vent.error( 'Attack on: '+str(vid) )
        return HttpResponse( 'Unregistered' )
    if not vent.is_registered:
        log_vent.error( 'Unregistered device contacting: '+str(vent.id) )
        return HttpResponse( 'Unregistered' )

    # check if there was a time
    past = datetime.now()-timedelta( seconds = 10 )
    if past > vent.last_contact:
        log_vent.error( 'Unregistered due to time gap: '+str(vent.id) )
        vent.is_registered = False
        vent.save()
        return HttpResponse( 'Unregistered' )

    context = RequestContext(request)
    context.push( {'vent': vent, 'data': data[vent.id] } )
    return render( request, 'ventme/display.html', context.flatten() )
    
