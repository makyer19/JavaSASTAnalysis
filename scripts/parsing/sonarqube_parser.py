import sys
import ijson

# Example command $ python sonarqube_parser.py sonarqubeOutput123.json

rulesFound = {}
file = open(sys.argv[1], "r")
for message in ijson.items(file, 'issues.item.message'):
    if message in rulesFound:
        rulesFound[message] += 1
    else: 
        rulesFound[message] = 1

sortedRules = sorted(rulesFound.items(), key=lambda x:x[1], reverse=True)
for key,value in sortedRules:
    print(key + " - " + str(value))