import lxml.etree as ET
import sys

# Example command $ python pmd_parser.py pmdOutput123.xml

FILTER_RULESET = ["Security"]

rulesFound = {}
parser = ET.XMLParser()
for action, elem in ET.iterparse(sys.argv[1]):
    if "violation" in elem.tag and elem.attrib.get("ruleset") in FILTER_RULESET:
        rule = elem.attrib.get("rule")
        if rule in rulesFound:
            rulesFound[rule] += 1
        else: 
            rulesFound[rule] = 1

sortedRules = sorted(rulesFound.items(), key=lambda x:x[1], reverse=True)
for key,value in sortedRules:
    print(key + " - " + str(value))