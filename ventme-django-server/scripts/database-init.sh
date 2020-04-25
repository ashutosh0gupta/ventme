# after changes in models.py, apps.py generate migration code
python3 manage.py makemigrations ventme
# apply the migration 
python3 manage.py migrate
python3 manage.py createsuperuser
