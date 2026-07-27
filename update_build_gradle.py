import sys

with open("app/build.gradle.kts", "r") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    new_lines.append(line)
    if "implementation(libs.firebase.crashlytics)" in line:
        new_lines.append("  implementation(libs.firebase.messaging)\n")
        new_lines.append("  implementation(libs.firebase.inappmessaging.display)\n")

with open("app/build.gradle.kts", "w") as f:
    f.writelines(new_lines)
