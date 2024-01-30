import sys, os

#Example command $ python runScan.py pmd,findsecuritybugs,semgrep,yasca,sonarqube ~/inputFiles.zip

programList = sys.argv[1].lower()
filePath = sys.argv[2]
command = "curl"

if "pmd" in programList:
    command = command + " -F pmd=true"
if "findsecuritybugs" in programList:
    command = command + " -F findsecuritybugs=true"
if "semgrep" in programList:
    command = command + " -F semgrep=true"
if "yasca" in programList:
    command = command + " -F yasca=true"
if "sonarqube" in programList:
    command = command + " -F sonarqube=true"

command = command + " -F file=@" + filePath + " --output output.zip http://localhost:8080/main-servlet"
os.system(command)