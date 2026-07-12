import re

with open("app/build/reports/tests/testDebugUnitTest/index.html", "r") as f:
    content = f.read()

# Try finding tests using plain regex match
match = re.search(r'<div class="counter">(\d+)</div>\s*<p>tests</p>', content)
match2 = re.search(r'<div class="counter">(\d+)</div>\s*<p>failures</p>', content)
match3 = re.search(r'<div class="counter">(\d+)</div>\s*<p>ignored</p>', content)

if match and match2:
    print(f"Tests: {match.group(1)}, Failures: {match2.group(1)}")
else:
    print("Could not parse test results")

failed_tests_match = re.search(r'<h2>Failed tests</h2>(.*?)</ul>', content, re.DOTALL)
if failed_tests_match and int(match2.group(1)) > 0:
    print("Failed Tests:")
    failed_tests = re.findall(r'<a href="classes/(.*?).html">(.*?)</a>', failed_tests_match.group(1))
    for cls, name in failed_tests:
        print(f"  {cls}: {name}")
