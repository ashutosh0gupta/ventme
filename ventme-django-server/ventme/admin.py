from django.contrib import admin

# Register your models here.
from .models import Ventilator,SystemState

admin.site.register(Ventilator)
admin.site.register(SystemState)

