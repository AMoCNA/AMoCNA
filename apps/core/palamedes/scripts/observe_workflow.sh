#!/bin/bash
set -e

# Configuration
GRAPHDB_URL=${GRAPHDB_URL:-http://localhost:7200}
REPOSITORY_ID=${GRAPHDB_REPO:-amocna}

# Namespaces
MOAM_NS="http://www.semanticweb.org/patryk/ontologies/2026/4/MoaMont#"
CNEE_NS="http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt/"

echo "---------------------------------------------------"
echo "Observing Palamedes Petri Net Workflows"
echo "Repository: $REPOSITORY_ID"
echo "---------------------------------------------------"

SPARQL_QUERY="
PREFIX moam: <$MOAM_NS>
PREFIX cnee: <$CNEE_NS>
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>

SELECT ?action ?state ?resource ?timestamp
WHERE {
  ?action moam:hasCurrentState ?stateIri .
  ?action moam:targetsEntity ?resourceIri .
  ?resourceIri cnee:resourceName ?resource .
  
  OPTIONAL { ?action moam:hasLastTransitionTimestamp ?timestamp }
  
  BIND(REPLACE(STR(?stateIri), \"^.*#\", \"\") AS ?state)
  
  FILTER(?stateIri IN (moam:State_Initial, moam:State_Planned, moam:State_Validated, moam:State_InProgress, moam:State_Compensating))
}
ORDER BY DESC(?timestamp)
"

while true; do
  clear
  echo "---------------------------------------------------"
  echo "Active Palamedes Workflows (Petri Net View)"
  echo "Last updated: $(date '+%Y-%m-%d %H:%M:%S')"
  echo "---------------------------------------------------"
  echo -e "ACTION\t\t\tSTATE\t\tRESOURCE\tTIMESTAMP"
  echo "---------------------------------------------------"

  curl -s -H "Accept: application/sparql-results+json" \
    --data-urlencode "query=$SPARQL_QUERY" \
    "$GRAPHDB_URL/repositories/$REPOSITORY_ID" |
    jq -r '.results.bindings[] | [.action.value, .state.value, .resource.value, .timestamp.value] | @tsv' |
    sed 's|http://www.semanticweb.org/patryk/ontologies/2026/4/MoaMont#||g' |
    column -t -s $'\t'

  echo "---------------------------------------------------"
  echo "Press [CTRL+C] to stop"
  sleep 2
done
