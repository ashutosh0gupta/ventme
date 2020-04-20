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