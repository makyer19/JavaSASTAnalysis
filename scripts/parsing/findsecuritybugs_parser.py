import lxml.etree as ET
import matplotlib as plot
import sys

# Example command $ python findsecuritybugs_parser.py pmdOutput123.xml

FILTER_RULESET = ["Error Prone", "Security"]

rulesFound = {}
parser = ET.XMLParser()
for action, elem in ET.iterparse(sys.argv[1]):
    if "ShortMessage" in elem.tag:
        rule = elem.text
        if rule in rulesFound:
            rulesFound[rule] += 1
        else: 
            rulesFound[rule] = 1

sortedRules = sorted(rulesFound.items(), key=lambda x:x[1], reverse=True)
for key,value in sortedRules:
    print(key + " - " + str(value))