import requests
import time

website="http://localhost:8000/"

reg_url = website+'register/'
reg_data = {'name': 'Ventilator2', 'location' : 'Room201'}

reg_reply = requests.post(reg_url, data = reg_data )

if reg_reply.text == 'BadFormat' :
    exit()

if reg_reply.text == 'Already':
    print( 'Wait for 10 secs for auto deregistration' )
    exit()

replies= reg_reply.text.split(' ')
dev_id = int(replies[0])
key = replies[1]


data_url = website+"data/" + str(dev_id) + "/"


for idx in range(1,60):
    sample_size = 19
    pressure = [ x/10 for x in range(0,sample_size)]
    airflow = [ x/10 for x in range(0,sample_size)]
    tidal = [ x*x for x in range(0,sample_size)]
    
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
        'peep_error':'False',
        'oxygen_error':'False',
        'ie_ratio_error':'False'
    }
    reply = requests.post( data_url, data = d )
    print(reply.text)
    if not reply.text in ['Registered','Dropped']:
        exit()
    time.sleep(1)
