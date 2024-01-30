import lxml.html as LH
import matplotlib as plot
import sys
import json

# Example command $ python yasca_parser.py yascaOutput123.html

rulesFound = {}
document = LH.fromstring(open(sys.argv[1], "r", encoding="utf8").read())
jsonString = document.get_element_by_id("resultsJson").text
jsonObjs = json.loads(jsonString)
for obj in jsonObjs:
    rule = obj["category"]
    if rule in rulesFound:
        rulesFound[rule] += 1
    else: 
        rulesFound[rule] = 1

sortedRules = sorted(rulesFound.items(), key=lambda x:x[1], reverse=True)
for key,value in sortedRules:
    print(key + " - " + str(value))