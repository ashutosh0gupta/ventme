# Random Attendance
For taking random attendance in the class

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
   ```

  -- the above list may not be exhaustive (let us know the missing dependencies)

 3.  Create file ./ventme_settings/.env containing the following key/values
 
SECRET_KEY="<django-secret-key>"
EMAIL_HOST="smtp.<your-smtp-server>"
EMAIL_HOST_USER="<user-on-smtp-server>"
EMAIL_HOST_PASSWORD="<password-of-the-user>"

* Use the following shell command to generate a random django-secret-key key

   $tr -dc 'a-z0-9!@#$%^&*(-_=+)' < /dev/urandom | head -c50

Note: every install must have a different secret key


  4. __Initialize db__

  ```
  $cd ./ventme-django-server
  $python3 manage.py makemigrations
  $python3 manage.py migrate
  ```
  ```
  $python3 manage.py createsuperuser
  ```

  5. __Using the server__

  - Start server of the application

   ```
   $cd ~/ventme
   $python3 manage.py runserver
   ```

  - For attendance, go to the following webpage in a browser

     http://127.0.0.1:8000/

Ventilator protocol

     http://127.0.0.1:8000/data

     http://127.0.0.1:8000/never

     http://127.0.0.1:8000/all

     http://127.0.0.1:8000/import

  - Policy of choosing a random student



# Other notes for development

(should not be relevant to a user)

- Django help

  https://docs.djangoproject.com/en/2.1/intro/tutorial02/
  
  https://www.youtube.com/watch?v=UmljXZIypDc&index=1&list=PL-osiE80TeTtoQCKZ03TU5fNfx2UY6U4p

- to create a app inside a new project

   $python manage.py startapp



# deploy modifications

  -- ventme_settings
  
      replace "deploy-prefix" to the choosen deploy-prefix

# test
curl -i -X POST --data "name=Ventilator1&location=Room101"  http://localhost:8000/register/

curl -i -X POST --data "reg_key=nrptfrnmwsbahnwp&packet_count=1&sample_rate=100&num_samples=3&set_oxygen=10&set_peep=5&set_rr=12&set_tidal_vol=300&set_ie_ratio=1:2&oxygen=40&pressure=20,20,20&airflow=10,10,10.1&tidal_volume=20,30,50&rr_error=False&peep_error=False&oxygen_error=False&ie_ratio_error=False"  http://localhost:8000/data/5/



# Django fixing

from django.utils.deprecation import RemovedInDjango30Warning

comment this line in below files

~/.local/lib/python3.6/site-packages/django/contrib/admin/templatetags/admin_static.py
~/.local/lib/python3.6/sitepackages/django/contrib/staticfiles/templatetags/staticfiles.py
