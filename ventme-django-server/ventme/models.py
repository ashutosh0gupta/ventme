from django.db import models

class Ventilator(models.Model):
    name=models.CharField(max_length=100)
    location=models.CharField(max_length=100, null = True)
    registration_key = models.CharField(max_length=16, null = True)
    last_contact = models.DateTimeField(verbose_name="Last contact", null=True)
    is_registered = models.BooleanField(verbose_name="Is registered?", default=False)
    packet_count =  models.IntegerField(verbose_name="Packet count",    default=0    )
    sample_rate =  models.IntegerField(verbose_name="Sample rate",    default=0    )

    # set values
    set_oxygen = models.IntegerField(verbose_name="Set Oxygen level(%)",    default=0 )
    set_peep = models.IntegerField(verbose_name="Set PEEP(H2Ocm)",    default=0 )
    set_rr = models.IntegerField(verbose_name="Set Respiratory Rate(pm)",    default=0 )
    set_tidal_vol = models.IntegerField(verbose_name="Set Tidal Vol(ml)",    default=0 )
    set_ie_ratio = models.CharField(verbose_name="Set IE Ratio", max_length=5, null = True )

    # readings
    oxygen = models.IntegerField(verbose_name="Oxygen level(%)",    default=0 )
    is_error = models.BooleanField(verbose_name="Is error?", default=False)    
    data = models.FileField(verbose_name="data dump file", null = True )
    
class SystemState(models.Model):
    SYS_MODE = ( ( 'ACTIVE', 'SYSTEM IS ACTIVE'), ('INACTIVE','SYSTEM IS INACTIVE') )
    # activeq = models.IntegerField(verbose_name="Question number", default=0    )
    # num_answered = models.IntegerField(verbose_name="Number of students answered", default=0)
    # num_attendance = models.IntegerField(verbose_name="Number of attendance", default=0)
    mode = models.CharField(verbose_name='Mode', choices=SYS_MODE, default='INACTIVE', max_length=20 )
