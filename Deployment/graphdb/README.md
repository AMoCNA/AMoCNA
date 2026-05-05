# Local GraphDB Setup

This directory contains the Docker Compose configuration to run a local GraphDB instance for Themis development.

## Quick Start

1. **Start the container:**
   ```bash
   docker-compose up -d
   ```

2. **Access the Workbench:**
   Open [http://localhost:7200](http://localhost:7200) in your browser.

3. **Create a Repository:**
   - Go to **Setup** -> **Repositories**.
   - Click **Create new repository** -> **GraphDB Repository**.
   - Set **Repository ID** to `moamont`.
   - Leave other settings as default and click **Create**.

4. **Import the Ontology:**
   - Go to **Import** -> **User data**.
   - Click **Upload RDF files**.
   - Select `ontology/MoaMont.owx` from the project root.
   - Click **Import** next to the uploaded file.
   - Use `http://www.semanticweb.org/patryk/ontologies/2026/4/MoaMont` as the Target graph (optional but recommended).

## Configuration for Themis

Themis is already configured in `application-dev.yml` to connect to `http://localhost:7200` with the repository ID `moamont`.
