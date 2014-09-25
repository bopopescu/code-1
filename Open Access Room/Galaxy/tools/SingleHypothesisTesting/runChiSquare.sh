#!/bin/sh

> $2_varMeanings
> $2_XML

cd $(dirname $0)
cd ../KnowledgeExtractionAndFilteringMechanism
python getCategoriesMeaning.py $1 $2_varMeanings

cd ../SingleHypothesisTesting
R --slave --args $1 $2 $3 $4 $5 $6 $2_XML $2_varMeanings < chiSquareTest.R
rm -f $2_varMeanings

cd ../KnowledgeExtractionAndFilteringMechanism
python workflowLogging.py $7 $1 $2 $2_XML 1
rm -f $2_XML
