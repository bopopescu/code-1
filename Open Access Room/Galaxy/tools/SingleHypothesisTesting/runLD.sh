#!/bin/sh

> $3_varMeanings
> $3_XML

cd $(dirname $0)
cd ../KnowledgeExtractionAndFilteringMechanism
python getCategoriesMeaning.py $1 $3_varMeanings

cd ../SingleHypothesisTesting
R --slave --args $1 $2 $3 $3_XML $3_varMeanings < linkageDisequilibrium.R 1>/dev/null
rm -f $3_varMeanings

cd ../KnowledgeExtractionAndFilteringMechanism
python workflowLogging.py $4 $1 $3 $3_XML 1
rm -f $3_XML
