# Ensure route modules are registered for both Render import paths:
# `app.main:app` and `backend.app.main:app`.
from . import main as _main
from . import sslcommerz as _sslcommerz_routes
