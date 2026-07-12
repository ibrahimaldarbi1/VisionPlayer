import re

with open("app/build/reports/tests/testDebugUnitTest/index.html", "r") as f:
    content = f.read()

match = re.search(r'<div class="infoBox" id="tests">\s*<div class="counter">(\d+)</div>', content)
match2 = re.search(r'<div class="infoBox" id="failures">\s*<div class="counter">(\d+)</div>', content)
match3 = re.search(r'<div class="infoBox" id="ignored">\s*<div class="counter">(\d+)</div>', content)
match4 = re.search(r'<div class="infoBox" id="duration">\s*<div class="counter">([^<]+)</div>', content)

if match and match2 and match3 and match4:
    print(f"Tests: {match.group(1)}, Failures: {match2.group(1)}, Ignored: {match3.group(1)}, Duration: {match4.group(1)}")
else:
    print("Could not parse test results")

failed_tests_match = re.search(r'<h2>Failed tests</h2>(.*?)</ul>', content, re.DOTALL)
if failed_tests_match and int(match2.group(1)) > 0:
    print("Failed Tests:")
    failed_tests = re.findall(r'<a href="classes/(.*?).html">(.*?)</a>', failed_tests_match.group(1))
    for cls, name in failed_tests:
        print(f"  {cls}: {name}")
