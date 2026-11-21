LocalBuds Custom Icon Drop-In Folder
===================================

Drop any of the optional PNG files listed below directly into this folder
(`app/custom_icons/`) and recompile LocalBuds (`./gradlew assembleDebug`).
Any file you leave out automatically uses LocalBuds' built-in minimalist
acoustic / Bose QC Ultra artwork. Delete a file and rebuild to revert.

Supported filenames (case-sensitive):

1. app_icon.png
   - Replaces the adaptive launcher icon foreground.
   - Recommended: 512x512 or 1024x1024 transparent PNG (centered within ~66% safe zone).

2. earbud_icon.png
   - Replaces the earbud artwork on the Home screen and Pop-Up Overlay.
   - Recommended: 256x256 or 512x512 transparent PNG oriented as the LEFT earbud;
     LocalBuds automatically mirrors it horizontally for the RIGHT earbud.

3. case_icon.png (optional)
   - Replaces the charging case artwork on the Home screen and Pop-Up Overlay.
   - Recommended: 256x256 or 512x512 transparent PNG.
