import lxml.etree as ET
import matplotlib as plot
import sys

# Example command $ python semgrep_parser.py semgrepOutput123.xml

rulesFound = {}
parser = ET.XMLParser()
for action, elem in ET.iterparse(sys.argv[1]):
    if "failure" in elem.tag:
        rule = elem.attrib.get("message").split('.')[0]
        if rule in rulesFound:
            rulesFound[rule] += 1
        else: 
            rulesFound[rule] = 1

sortedRules = sorted(rulesFound.items(), key=lambda x:x[1], reverse=True)
for key,value in sortedRules:
    print(key + " - " + str(value))
