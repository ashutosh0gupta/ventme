from django.urls import path, re_path
from django.conf import settings
from django.conf.urls.static import static

from . import views

urlpatterns = [
    path( '', views.index, name='index'),
    path( 'all/', views.all_status, name='all'),
    path( 'register/', views.register, name='register' ),
    re_path( 'data/(?P<vid>\d+)/', views.data, name='data'),
    re_path( 'vent/(?P<vid>\d+)/', views.vent, name='vent' ),
    re_path( 'plot/(?P<vid>\d+)/', views.plot_data, name='plot'),

] + static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)

    
    # re_path(r'^(?P<rollno>\d\d[0-9DV]+\d+)/$', views.status, name='status'),
    # path('startq/', views.startq, name='startq'),
    # path('stopq/', views.stopq, name='stopq'),
    # path('createq/', views.CreateQuestion.as_view(), name='createq'),
    # re_path(r'^viewq/(?P<qid>\d+)/', views.viewq, name='viewq'),
    # re_path(r'^editq/(?P<qid>\d+)/', views.EditQuestion.as_view(), name='editq'),    
    # re_path(r'^deleteq/(?P<qid>\d+)/$', views.deleteq, name='deleteq'),
    # re_path(r'^activateq/(?P<qid>\d+)/$', views.activateq, name='activateq'),     re_path(r'^answer/(?P<ansid>\d+)/', views.StudentResponse.as_view(), name='answer'),    

    # re_path(r'^(?P<rollno>[0-9DV]+)/$', views.status, name='status'),
