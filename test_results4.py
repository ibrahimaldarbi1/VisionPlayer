import re

with open("app/build/reports/tests/testDebugUnitTest/index.html", "r") as f:
    content = f.read()

match = re.search(r'<div class="counter">(\d+)</div>\s*<p>tests</p>', content)
match2 = re.search(r'<div class="counter">(\d+)</div>\s*<p>failures</p>', content)
match3 = re.search(r'<div class="counter">(\d+)</div>\s*<p>ignored</p>', content)

if match and match2:
    print(f"Tests: {match.group(1)}, Failures: {match2.group(1)}")
else:
    print("Could not parse test results")
