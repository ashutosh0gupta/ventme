#!/usr/bin/python3

import requests
import time

f = open('./scripts/raw_data.txt', "r")


website="http://localhost:8000/"

reg_url = website+'register/'
reg_data = { 'name': 'Ventilator2',
             'location' : 'Room201',
             'patient' : 'Ibn Al Massod',
              'protocol_version' : '1.0' }

reg_reply = requests.post(reg_url, data = reg_data )

if reg_reply.text == 'BadFormat' :
    exit()

if reg_reply.text == 'Already':
    print( 'Wait for 10 secs for auto deregistration' )
    exit()
    
print(reg_reply.text)
replies= reg_reply.text.split(' ')
dev_id = int(replies[0])
key = replies[1]


data_url = website+"data/" + str(dev_id) + "/"

sample_size = 20
pressure = [ 0 for x in range(0,sample_size)]
airflow = [ 0 for x in range(0,sample_size)]
tidal = [ 0 for x in range(0,sample_size)]

for idx in range(1,150):

    for j in range(0,sample_size):
        line = f.readline()
        if line:
            splt = line.split()
        else:
            exit()
        pressure[j] = float(splt[0])
        airflow[j] = float(splt[1])
        tidal[j] = j*j
    
    
    d = {
        'reg_key':key,
         'packet_count':idx,
        'sample_rate':100,
        'num_samples':20,
        'set_oxygen':10,
        'set_peep':5,
        'set_rr':12,
        'set_tidal_vol':300,
        'set_ie_ratio':"1:2",
        'oxygen':40,
        'pressure':','.join([str(i) for i in pressure]),
        'airflow':','.join([str(i) for i in airflow]),
        'tidal_volume':','.join([str(i) for i in tidal]),
        'rr_error':'False',
        'peep_error':'True',
        'pmax_error':'True',        
        'oxygen_error':'False',
        'ie_ratio_error':'True'
    }
    reply = requests.post( data_url, data = d )
    print(reply.text)
    if not reply.text in ['Success','Dropped']:
        break
    time.sleep(0.2)

f.close()
