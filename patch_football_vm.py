import re

with open("app/src/main/java/com/example/ui/feature/football/FootballViewModel.kt", "r") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    new_lines.append(line)

def insert_after(func_name, code_to_insert):
    for i, line in enumerate(new_lines):
        if line.strip().startswith("fun " + func_name + "("):
            new_lines.insert(i + 1, code_to_insert + "\n")
            break
        elif line.strip().startswith("override fun " + func_name + "("):
            new_lines.insert(i + 1, code_to_insert + "\n")
            break

def replace_if_exists(func_name, code_to_insert):
    for i, line in enumerate(new_lines):
        if line.strip().startswith("fun " + func_name + "("):
            # check if the check already exists
            found = False
            for j in range(i+1, i+5):
                if j < len(new_lines) and "isFootballAvailable(" in new_lines[j]:
                    found = True
                    break
                if j < len(new_lines) and "shouldShowFootball(" in new_lines[j] and func_name != "onProfileChanged":
                    new_lines[j] = code_to_insert + "\n"
                    found = True
                    break
            if not found:
                new_lines.insert(i + 1, code_to_insert + "\n")
            break

# Add isFootballAvailable
availability_func = """
    private fun isFootballAvailable(providerId: String? = null): Boolean {
        val profile = currentProfile ?: return false
        if (!com.example.ui.feature.shell.FeatureAvailabilityPolicy.shouldShowFootball(profile.features)) return false
        if (providerId != null && profile.providerId != providerId) return false
        return true
    }
"""
# insert before the last brace
for i in range(len(new_lines)-1, -1, -1):
    if new_lines[i].strip() == "}":
        new_lines.insert(i, availability_func)
        break

check_no_args = "        if (!isFootballAvailable()) return"
check_provider_id = "        if (!isFootballAvailable(providerId)) return"

replace_if_exists("onHomeVisibilityChanged", check_provider_id)
replace_if_exists("onScheduleCriteriaChanged", check_provider_id)
replace_if_exists("loadCompetitions", check_provider_id)
replace_if_exists("loadSchedule", check_provider_id)
replace_if_exists("retrySchedule", check_provider_id)
replace_if_exists("openSetupDialog", check_no_args)
replace_if_exists("openSettingsDialog", check_no_args)
replace_if_exists("selectCompetition", check_no_args)
replace_if_exists("selectAllCompetitions", check_no_args)
replace_if_exists("clearCompetitionSelection", check_no_args)
replace_if_exists("saveInitialSetupSelection", check_provider_id)
replace_if_exists("saveSettingsSelection", check_provider_id)
replace_if_exists("setShowOnHome", check_provider_id)

with open("app/src/main/java/com/example/ui/feature/football/FootballViewModel.kt", "w") as f:
    f.writelines(new_lines)

