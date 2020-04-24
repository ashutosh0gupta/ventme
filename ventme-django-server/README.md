# Setting up
  1. __Copy this code to the following folder__

   ```
   ./ventme-django-server
   ```

  2. __Install dependencies: Django and the others__
  
   ```
   $sudo apt update
   $sudo apt-get install python3 python3-pip mysql-server
   $pip3 install Django
   $pip3 install django-mathfilters
   $pip3 install -r /path/to/requirements.txt
   ```

  -- the above list may not be exhaustive (let us know the missing dependencies)

 3.  Create file ./ventme_settings/.env containing the following key/values

```
SECRET_KEY="<django-secret-key>"
AUTH_LDAP_SERVER_URI="<ldap-server-for-account-management>"
EMAIL_HOST="smtp.<your-smtp-server>"
EMAIL_HOST_USER="<user-on-smtp-server>"
EMAIL_HOST_PASSWORD="<password-of-the-user>"
```

* Use the following shell command to generate a random django-secret-key key

   $tr -dc 'a-z0-9!@#$%^&*(-_=+)' < /dev/urandom | head -c50

Note: every install must have a different secret key
   * We are currently not sending any notification emails. Therefore, no
     need to set up any meaningful values for emails.

  4. __Initialize db__

  ```
  $cd ./ventme-django-server
  $python3 manage.py makemigrations ventme
  $python3 manage.py migrate
  $python3 manage.py createsuperuser
  ```

  # __Using the server__

  - Start server 

   ```
   $cd ~/ventme-django-server
   $python3 manage.py runserver
   ```

  - For dashboard, go to the following webpage in a browser

     http://localhost:8000/

    It will ask you to login. login via the just created superuser.

    Login should take you to http://localhost:8000/all

  - Run the following dummy ventilator
   ```
   ./scripts/vent-dummy.py
   ```
  - Refesh http://localhost:8000/all

  - Click on the ventilator that now appears in the list

  # __Ventilator protocol__

  - _View_
  
     http://localhost:8000/all  View all ventilators

     http://localhost:8000/vent/[id] View plots of ventilator [id]

  - _communcation with ventilators_

   ```
     http://127.0.0.1:8000/register/
       post { 'name': '<unqiue-name : string <100 chars>',
              'location' : '<location of ventilator : string < 100 chars >',
              'patient' : '<patient on the ventilator : string < 100 chars >',
              'protocol_version' : '1.0'
            }
       reponses  "<id> <key : 16 char string>"
                 "Already"    -- in case the ventilator was already registered
                 "BadFormat"  -- in case post is not in good format

     http://127.0.0.1:8000/data/<id>/
       post {
        'reg_key': <key>,
        'packet_count':<packet-counter-incremented-round over:32768>,
        'sample_rate':<sampling-rate : Int>,
        'num_samples':< N = number-of-samples-in-this-message : Int >,
        'set_oxygen':<set-oxygen-level : Int %>,
        'set_peep':<set-peep-level : float (H2Ocm)>,
        'set_rr':<set-rr-level : Int (pm)>,
        'set_tidal_vol':<set-tidal-vol : Int (ml)>,
        'set_ie_ratio':<set-I:E-ratio : String e.g. "1:2">,
        'oxygen': <observed-level-of-oxygen : Int % >,
        'pressure': <N pressure-samples: comma-separate-floats (H2Ocm)>,
        'airflow':<N airflow-samples: comma-separate-floats (lpm)>,
        'tidal_volume':<N tidal-samples: comma-separate-floats (ml)>,
        'rr_error':  <Respiration error: [True,False]>,
        'peep_error': <PEEP error: ['True',False]>,
        'pmax_error': <Pmax error: ['True',False]>,
        'oxygen_error': <OXYGEN error: ['True','False']>,
        'ie_ratio_error':<IE ratio error: ['True','False']>
         }
      reponses  "Unregistered" -- Device is disconnected from the server 
                "BadFormat"    -- in case post is not in compliance
                "Dropped"      -- If a packet drop detected
                "Success"      -- If packet received successfully
   ```


# deploy modifications

  - In ventme_settings/settings.py set DEPLOY to True   
   ```
   DEPLOY = True
   ```
  - In ventme_settings/deploy_settings.py

   ```
       DEPLOY_PREFIX=<set-a-value>
   ```

# Quick tests to communicate with the server

   ```
curl -i -X POST --data "name=Ventilator1&location=Room101"  http://localhost:8000/register/

curl -i -X POST --data "reg_key=<key>&packet_count=1&sample_rate=100&num_samples=3&set_oxygen=10&set_peep=5&set_rr=12&set_tidal_vol=300&set_ie_ratio=1:2&oxygen=40&pressure=20,20,20&airflow=10,10,10.1&tidal_volume=20,30,50&rr_error=False&peep_error=False&oxygen_error=False&ie_ratio_error=False"  http://localhost:8000/data/<id>/
   ```


# Other notes for development

(should not be relevant to a user)

- Django help

  https://docs.djangoproject.com/en/2.1/intro/tutorial02/
  
  https://www.youtube.com/watch?v=UmljXZIypDc&index=1&list=PL-osiE80TeTtoQCKZ03TU5fNfx2UY6U4p

- to create a app inside a new project

   $python manage.py startapp


####### Django fixing if using 3.0

Comment the following line

   ```
from django.utils.deprecation import RemovedInDjango30Warning
   ```

in the following files

   ```
~/.local/lib/python3.6/site-packages/django/contrib/admin/templatetags/admin_static.py
~/.local/lib/python3.6/sitepackages/django/contrib/staticfiles/templatetags/staticfiles.py
   ```
