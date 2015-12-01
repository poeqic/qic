; QIC (Quasi-In-Chat) Search
;
; Written by:
; /u/Eruyome87 
; /u/ProFalseIdol
;
; Latest Version will always be at:
; https://github.com/thirdy/wts/
;
; Feel free to make pull-requests.
;

IfWinActive, Path of Exile ahk_class Direct3DWindowClass
#SingleInstance force ; If it is alReady Running it will be restarted.
#NoEnv  ; Recommended for performance and compatibility with future AutoHotkey releases.
#Persistent ; Stay open in background
SendMode Input  ; Recommended for new scripts due to its superior speed and reliability.
SetWorkingDir %A_ScriptDir%  ; Ensures a consistent starting directory.
SetBatchLines, -1

#Include, lib/Gdip_All.ahk
; https://www.autohotkey.com/boards/viewtopic.php?t=1879
#Include, lib/Gdip_Ext.ahk
; https://github.com/cocobelgica/AutoHotkey-JSON
#Include, lib/JSON.ahk

Menu, tray, Tip, Path of Exile - QIC (Quasi-In-Chat) Search
Menu, tray, Icon, resource/qic$.ico

If (A_AhkVersion <= "1.1.22")
{
    msgbox, You need AutoHotkey v1.1.22 or later to run this script. `n`nPlease go to http://ahkscript.org/download and download a recent version.
    exit
}

; Set Hotkey for toggling GUI overlay completely OFF, default = ctrl + q
; ^p and ^i conflicts with trackpetes ItemPriceCheck macro
Hotkey, ^q, ToggleGUI
; Set Hotkeys for browsing through search results
Hotkey, PgUp, PreviousPage
Hotkey, PgDn, NextPage

; Start gdi+
If !pToken := Gdip_Startup()
{
   MsgBox, 48, gdiplus error!, Gdiplus failed to start. Please ensure you have gdiplus on your system
   ExitApp
}
OnExit, Exit

parm1 = %1%  ; first input parameter
parm2 = %2%  ; Second input parameter

if (parm1 = "$EXIT") 
{
	ExitApp
} 
;MsgBox, % parm1 ; ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~  REMOVE ME   !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
StringReplace, param1, parm1, $LF, `n, All
StringReplace, param2, parm2, $LF, `n, All
global PageSize = 5
global PageNumbers := 0
global ResultPages := []
global SearchResults := []
global LastSelectedPage := 1
global TextToDraw = ""

; https://github.com/tariqporter/Gdip/blob/master/Gdip.Tutorial.8-Write.text.onto.a.gui.ahk
; Set the width and height we want as our drawing area, to draw everything in. This will be the dimensions of our bitmap
WinGetPos, Xpos, Ypos, ScreenWidth, ScreenHeight, Path of Exile ahk_class Direct3DWindowClass
Font := CheckFont("Arial")
DrawingAreaWidth 	:= ReadValueFromIni("Width", 310)
DrawingAreaPosX 	:= ReadValueFromIni("AbsolutePositionLeft", ceil(ScreenWidth * 0.33 + DrawingAreaWidth))
DrawingAreaPosY 	:= ReadValueFromIni("AbsolutePositionTop", 5)
DrawingAreaHeight	:= ReadValueFromIni("Height", (ScreenHeight - 50))
FontSize 			:= ReadValueFromIni("FontSize", 13)
PageSize 			:= ReadValueFromIni("PageSize", 5)
	
Gui, 1:  -Caption +E0x80000 +LastFound +OwnDialogs +Owner +AlwaysOnTop
Gui, 1: Show, NA

hwnd1 := WinExist()
hbm := CreateDIBSection(DrawingAreaWidth, DrawingAreaHeight)
hdc := CreateCompatibleDC()
obm := SelectObject(hdc, hbm)
G := Gdip_GraphicsFromHDC(hdc)
Gdip_SetSmoothingMode(G, 4)

Gosub, DrawOverlay
; Extra options:
; ow4         - Sets the outline width to 4
; ocFF000000  - Sets the outline colour to opaque black
; OF1			- If this option is set to 1 the text fill will be drawn using the same path that the outline is drawn.
Options = x5 y5 w%DrawingAreaWidth%-10 h%DrawingAreaHeight%-10 Left cffffffff ow2 ocFF000000 OF1 r4 s%FontSize%
TextToDraw := param1
Gosub, DrawText

OnMessage(0x201, "WM_LBUTTONDOWN")

Gosub, CheckWinActivePOE
SetTimer, CheckWinActivePOE, 100
GuiON = 1

FileRead, JSONFile, sample.json
parsedJSON 	:= JSON.Load(JSONFile)
Command 		:= parsedJSON.command
Input 			:= parsedJSON.input
ItemResults 	:= parsedJSON.itemResults
Gosub, PageSearchResults

Return


WM_LBUTTONDOWN() {
   PostMessage, 0xA1, 2
}

; ------------------ TOGGLE GUI ------------------ 
ToggleGUI:
	If (GuiON = 0) {
		Gosub, CheckWinActivePOE
		SetTimer, CheckWinActivePOE, 100
		GuiON = 1
	}
	Else {
		SetTimer, CheckWinActivePOE, Off      
		Gui, 1: Hide	
		GuiON = 0
	}
Return

; ------------------ SHOW NEXT PAGE ------------------ 
NextPage:	
	If LastSelectedPage < %PageNumbers%
		LastSelectedPage += 1
	Else
		Return
	
	Gosub, DrawOverlay
	TextToDraw := ResultPages[LastSelectedPage]
	Gosub, DrawText	
Return

; ------------------ SHOW PREVIOUS PAGE ------------------ 
PreviousPage:
	If LastSelectedPage > 1
		LastSelectedPage -= 1
	Else
		Return
	
	Gosub, DrawOverlay	
	TextToDraw := ResultPages[LastSelectedPage]
	Gosub, DrawText	
Return

DrawText:
	Gdip_TextToGraphicsOutline(G, TextToDraw, Options, Font, DrawingAreaWidth, DrawingAreaHeight)
	UpdateLayeredWindow(hwnd1, hdc, DrawingAreaPosX, DrawingAreaPosY, DrawingAreaWidth, DrawingAreaHeight)	
Return


; ------------------ DRAW (REDRAW) OVERLAY ------------------ 
; https://github.com/tariqporter/Gdip/blob/master/Gdip.Tutorial.8-Write.text.onto.a.gui.ahk
; Set the width and height we want as our drawing area, to draw everything in. This will be the dimensions of our bitmap
DrawOverlay:
	Gdip_GraphicsClear(G)
	pBrush := Gdip_BrushCreateSolid(0xffb4804b)
	; left border
	Gdip_FillRectangle(G, pBrush, 0, 0, 1, DrawingAreaHeight)
	; right border
	Gdip_FillRectangle(G, pBrush, DrawingAreaWidth - 2, 0, 1, DrawingAreaHeight)
	; top border
	Gdip_FillRectangle(G, pBrush, 0, 1, DrawingAreaWidth, 1)
	; bottom border
	Gdip_FillRectangle(G, pBrush, 0, DrawingAreaHeight - 2, DrawingAreaWidth, 1)
	; background
	pBrush := Gdip_BrushCreateSolid(0x47000000)
	Gdip_FillRectangle(G, pBrush, 0, 0, DrawingAreaWidth, DrawingAreaHeight)
Return

; ------------------ READ INI AND CHECK IF VARIABLES ARE SET ------------------ 
ReadValueFromIni(IniKey, DefaultValue){
	IniRead, OutputVar, overlay_config.ini, Overlay, %IniKey%
	If !OutputVar
		OutputVar := DefaultValue
	Return OutputVar
}

; ------------------ READ FONT FROM INI AND CHECK IF INSTALLED ------------------
CheckFont(DefaultFont){
	; Next we can check that the user actually has the font that we wish them to use
	; If they do not then we can do something about it. I choose to default to Arial.
	IniRead, InputFont, overlay_config.ini, Overlay, FontFamily
	If !hFamily := Gdip_FontFamilyCreate(InputFont)	{
	   OutputFont := DefaultFont
	}
	Else {
		Gdip_DeleteFontFamily(hFamily)
		OutputFont := InputFont
	}
	Return OutputFont
}

; ------------------ PAGE SEARCH RESULTS ------------------
PageSearchResults:
	SearchResults := ItemObjectsToString(ItemResults)
	PageNumbers := ceil(SearchResults.MaxIndex() / PageSize)
	If PageNumbers = 0
		PageNumbers := 1
	
	LastIndex = 0
	Loop %PageNumbers%
	{
		Page := parsedJSON.league "`r`n"
		Loop %PageSize%
		{
			Page .= SearchResults[A_Index+LastIndex]
		}
		If !SearchResults[1] {
			Page .= "_______________________________________________" "`r`n" "`r`n"
			Page .= "0 search results."
		}
		LastIndex := PageSize * A_Index
		ResultPages.Insert(Page)
	}
	
	Gosub, DrawOverlay
	TextToDraw := ResultPages[LastSelectedPage]
	Gosub, DrawText
Return

; ------------------ RETURN PRINTABLE ITEMS ------------------ 
ItemObjectsToString(ObjectArray){
	oa := ObjectArray
	o := []
	s := 	
	
	for i, e in oa {
		su =
		; Add item index, name, sockets and quality			
		su .= "_______________________________________________" "`r`n"
		su .= "[" e.id "] " e.name
		If e.socketsRaw {
			su .= " " e.socketsRaw 
		}
		If e.quality {
			su .= " " Floor(e.quality) "%"
		}
		su .= "`r`n"
		
		; Add implicit mod
		If e.implicitMod {
			temp := StrReplace(e.implicitMod.name, "#",,,1)
			temp := StrReplace(temp, "#", Floor(e.implicitMod.value))
			su .= temp	 "`r`n"
		}
		su .= "-----------"
		
		; Add explicit mods
		If e.explicitMods.MaxIndex() > 0 {
			for j, f in e.explicitMods {
				temp := StrReplace(f.name, "#",,,1)
				temp := StrReplace(temp, "#", Floor(f.value))
				su .= "`r`n" temp				
			}
		}	
		If e.identified = 0 {
			su .= "`r`n" "Unidentified"
		}	
		su .= "`r`n" "-----------" "`r`n"
		
		; Corrupted Tag
		If e.corrupted = 1 {
			su .= "Corrupted" "`r`n"
		}	
		
		; Add defense
		If e.armourAtMaxQuality || e.energyShieldAtMaxQuality || e.evasionAtMaxQuality || e.block {
			If e.armourAtMaxQuality && e.energyShieldAtMaxQuality { 
				su .= "AR: " Floor(e.armourAtMaxQuality) " " "ES: " Floor(e.energyShieldAtMaxQuality)
			}
			Else If e.armourAtMaxQuality && e.evasionAtMaxQuality {
				su .= "AR: " Floor(e.armourAtMaxQuality) " " "EV: " Floor(e.evasionAtMaxQuality)
			}
			Else If e.evasionAtMaxQuality && e.energyShieldAtMaxQuality {
				su .= "EV: " Floor(e.evasionAtMaxQuality) " " "ES: " Floor(e.energyShieldAtMaxQuality)
			}
			Else If e.armourAtMaxQuality  {
				su .= "AR: " Floor(e.armourAtMaxQuality)
			}
			Else If e.evasionAtMaxQuality  {
				su .= "EV: " Floor(e.evasionAtMaxQuality)
			}
			Else If e.energyShieldAtMaxQuality  {
				su .= "ES: " Floor(e.energyShieldAtMaxQuality)
			}
			Else If e.armourAtMaxQuality && e.evasionAtMaxQuality && e.energyShieldAtMaxQuality {
				su .= "AR: " Floor(e.armourAtMaxQuality) " " "EV: " Floor(e.evasionAtMaxQuality) " " "ES: " Floor(e.energyShieldAtMaxQuality)
			}
			If e.block {
				su .= " Block: " Floor(e.block)
			}
		}
		
		; Add pdps, edps, aps and critchance
		If e.physDmgAtMaxQuality || e.eleDmg || e.attackSpeed || e.crit {
			If e.physDmgAtMaxQuality {
				su .= "pDPS " e.physDmgAtMaxQuality " "
			}
			If e.eleDmg {
				su .= "eDPS " e.eleDmg " "			
			}
			If e.attackSpeed {
				su .= "APS " e.attackSpeed " "
			}
			If e.crit {
				su .= "CC " e.crit
			}
		}
	
		; Add required stats
		If e.reqLvl || e.reqStr || e.reqInt || e.reqDex {
			su .= " | "
			If e.reqLvl {
				su .= "Lvl " e.reqLvl " "
			} 
			If e.reqStr {
				su .= "Str " e.reqStr " "
			}
			If e.reqInt {
				su .= "Int " e.reqInt " "
			}
			If e.reqDex {
				su .= "Dex " e.reqDex
			}
		}
		
		; Add price, ign
		su .= "`r`n" e.buyout " " "IGN: " e.ign "`r`n"
		o[i] := su
		;msgbox % su
	}
		
	return o
}

; ------------------ HIDE/SHOW OVERLAY IF GAME IS NOT ACTIVE/ACTIVE ------------------
CheckWinActivePOE: 
	GuiControlGet, focused_control, focus
	if(WinActive("ahk_class Direct3DWindowClass") && WinActive("Path of Exile"))
		If (GuiON = 0) {
			Gui, 1: Show, NA
			GuiON := 1
		}
	if(!WinActive("ahk_class Direct3DWindowClass") && !WinActive("Path of Exile"))
		;If !focused_control
		If (GuiON = 1)
		{
			Gui, 1: Hide
			GuiON = 0
		}
Return

; ------------------ EXIT ------------------ 
Exit:
	Gdip_DeleteBrush(pBrush)
	SelectObject(hdc, obm)
	DeleteObject(hbm)
	DeleteDC(hdc)
	Gdip_DeleteGraphics(G)
	Gdip_Shutdown(pToken)
	ExitApp
Return