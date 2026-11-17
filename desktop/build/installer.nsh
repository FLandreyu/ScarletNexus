; Refuse replacement while the product is running. Never force-kill services.
!macro customCheckAppRunning
  ; This runs before uninstallOldVersion, including in silent installs.
  ; An owned data root or PostgreSQL cluster must never become an install tree.
  GetFullPathName $R8 "$INSTDIR"
  ${Do}
    ${If} ${FileExists} "$R8\owner.json"
    ${OrIf} ${FileExists} "$R8\PG_VERSION"
      IfSilent +2
        MessageBox MB_OK "Choose an installation folder outside your learning data and database."
      SetErrorLevel 2
      Quit
    ${EndIf}
    StrCpy $R9 $R8
    ${GetParent} "$R8" $R8
    ${If} $R8 == $R9
      ${Break}
    ${EndIf}
  ${LoopUntil} $R8 == ""
  nsProcess::_FindProcess "${APP_EXECUTABLE_FILENAME}"
  Pop $0
  ${If} $0 == 0
    IfSilent +2
      MessageBox MB_OK "Please close Scarlet Nexus before installing or uninstalling. Learning data will be preserved."
    SetErrorLevel 2
    Quit
  ${EndIf}
!macroend
