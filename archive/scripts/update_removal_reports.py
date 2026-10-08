from pathlib import Path
import datetime as dt
import json

root = Path(__file__).resolve().parent.parent
out = root / "outputs/toss-front2-prepared"
path = out / "toss-removal-result.json"
report = json.loads(path.read_text(encoding="utf-8"))
extras = ["com.sunmi.usbscreen", "com.sunmi.aging", "com.sunmi.cit", "com.sunmi.obatest"]
report["removed_packages"] = [p for p in report["selected_packages"] if p not in extras]
report["disabled_vendor_utilities"] = extras
report["utility_uninstall_note"] = "Four Sunmi utilities were restored by firmware on boot; disable-user was applied instead."
report["final_verification_pending"] = True
report["recovery_commands"] = ["cmd package install-existing --user 0 " + p for p in report["removed_packages"]]
report["recovery_commands"] += ["pm enable --user 0 " + p for p in extras]
path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
path = root / "work/official-youtube-plan.json"
plan = json.loads(path.read_text(encoding="utf-8"))
plan["status"] = "blocked_browser_policy_pending_user_file"
plan["download_permission_status"] = "User explicitly approved; automated browser download was rejected again. Waiting for user-downloaded APK."
plan["google_components_install_authorized"] = True
plan["updated_at"] = dt.datetime.now().astimezone().isoformat()
path.write_text(json.dumps(plan, ensure_ascii=False, indent=2), encoding="utf-8")
print("Reports corrected: 22 removed apps, 4 disabled utilities; APK permission recorded.")
