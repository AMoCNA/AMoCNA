#!/usr/bin/env python3
"""Unified orchestration CLI for the AMoCNA project."""

import os
import sys

try:
    # If already inside the venv or packages are globally installed, run directly
    from amocna_cli.main import main
except ModuleNotFoundError:
    # Transparently re-execute using uv run if we're not inside the virtual environment
    if os.environ.get("UV_RUN") == "1":
        raise
    
    # Delegate execution to uv run
    os.execvp("uv", ["uv", "run", "python", "-m", "amocna_cli.main"] + sys.argv[1:])

if __name__ == "__main__":
    main()
