/*

Copyright 2022 - tomw

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

-------------------------------------------

Change history:

0.9.6 - tomw - Additional attributes for dishwasher
0.9.2 - tomw - Make child logging follow system device setting
0.9.0 - tomw - Initial release

*/

metadata 
{
    definition(name: "SmartHQ Dishwasher", namespace: "tomw", author: "tomw") 
    {
        capability "ContactSensor"
        capability "Refresh"
        
        command "setSabbathMode", [[name: "Sabbath mode enable?*", type:"ENUM", constraints: [true, false]]]
        command "setSoundLevel", [[name: "Sound level setting*", type:"ENUM", constraints: [true, false]]]
        command "setControlLock", [[name: "Control Lockout*", type:"ENUM", constraints: [true, false]]]
        command "setPodCount", [[name: "Set Pod Count*", type:"NUMBER", constraints: [1..99]]]
        command "start"
        command "stop"
        
        attribute "cycleMode", "string"
        //attribute "cycleName", "string"
        attribute "cycleState", "string"
        attribute "delayHours", "number"
        attribute "doorStatus", "enum", ["open", "closed"]
        attribute "mode", "string"
        attribute "sound", "enum", ["enable", "disable"]
        attribute "timeRemaining", "number"
        //attribute "podCount", "number"
    }
}

#include tomw.smarthqHelpers

def refresh()
{
    refreshAppliance()
}

def parse(item)
{
    if(!item)
    {
        return
    }
    
    logDebug(item)
    
    switch(item.erd?.toLowerCase())
    {
        case DISHWASHER_DOOR_STATUS:
            parseDoorStatusByte(item.value, doorNormallyOpen)
            break
        
        /*case DISHWASHER_CYCLE_NAME:
            sendEvent(name: "cycleName", value: decodeErdString(item.value))
            break   */
        
        case DISHWASHER_CYCLE_STATE:
            parseCycleState(item.value)
            break
        
        case DISHWASHER_TIME_REMAINING:
            sendEvent(name: "timeRemaining", value: decodeErdInt(hubitat.helper.HexUtils.hexStringToByteArray(item.value)))
            break
        
        case DISHWASHER_OPERATING_MODE:
            parseMode(item.value)
            break
        
        case DISHWASHER_USER_SETTING:
            parseUserSetting(item.value)
            break            
    }
}

def parseCycleState(value)
{
    def bytes = hubitat.helper.HexUtils.hexStringToByteArray(value)
    
    def cState = knownStates[decodeErdInt(subBytes(bytes, 0, 1))]
    
    sendEvent(name: "cycleState", value: (null != cState) ? cState : "unknown")
}

def parseMode(value)
{
    def bytes = hubitat.helper.HexUtils.hexStringToByteArray(value)
    
    def mode = knownModes[decodeErdInt(subBytes(bytes, 0, 1))]
    
    sendEvent(name: "mode", value: (null != mode) ? mode : "unknown")
}

def parseUserSetting(value)
{
    value = decodeErdInt(hubitat.helper.HexUtils.hexStringToByteArray(value))
    
    def bottle_jet = UserSetting.getAt(value & 1)
    def cycleMode = UserCycleSetting.getAt((value & 0x0E) >> 1)
    def sabbath = UserSetting.getAt((value & 0x40) >> 6)
    def presoak = UserSetting.getAt((value & 0x100) >> 8)
    def lock_control = UserSetting.getAt((value & 0x200) >> 9)
    def dry_option = UserDryOptionSetting.getAt((value & 0xC00) >> 10)
    def wash_temp = UserWashTempSetting.getAt((value & 0x3000) >> 12)
    def delay_hours = ((value & 0xF0000) >> 16)
    def wash_zone = UserWashZoneSetting.getAt((value & 0x300000) >> 20)
    def sound = SoundSetting.getAt((value & 0x800000) >> 23)
    //def podCount = ((value & 0x3100) >> 4)   ??  

    
    def events = [[:]]
    
    events += [name: "cycleMode", value: cycleMode]
    events += [name: "delayHours", value: delay_hours]
    events += [name: "sound", value: (null != sound) ? sound : "unknown"]
    //events += [name: "podCount", value: podCount]
    
    flushEvents(events)
}


def setPodCount(data)
{
    def value = encodeErdInt(data.toInteger(), 2)
    def erdMap = buildErdSetter(buildDevDetails(), buildErdDetails(DISHWASHER_PODS_REMAINING_VALUE, value))
    parent?.sendWssMap(erdMap)
}

def start()
{
    def devMac = device.data.mac
    def user = parent.data.userId
    def erdMap = startPost(user,devMac)
    
    parent?.sendWssMap(erdMap)
}

def stop()
{
    def devMac = device.data.mac
    def user = parent.data.userId
    def erdMap = stopPost(user,devMac)
    
    parent?.sendWssMap(erdMap)
}

import groovy.transform.Field

@Field Map knownModes = 
    [
        0: "low power",
        "0E": "power up",
        1: "power up",
        2: "standby",
        3: "delay start",
        4: "pause",
        5: "active",
        6: "end",
        7: "download",
        8: "sensor check",
        9: "load activation",
        19: "control locked"
    ]

@Field Map knownStates = 
    [
        0: "no change",
        1: "prewash",
        2: "sensing",
        3: "main wash",
        4: "drying",
        5: "sanitizing",
        7: "starting",  //??
        8: "pause",
        9: "rinsing",
        10: "prewash1",
        11: "final rinse",
        12: "end prewash1",
        16: "final rinse fill",
        17: "inactive",
        26: "inactive"
    ]

@Field Map UserSetting =
    [ 0: "disabled", 1: "enabled", 0xFF: "invalid" ]

@Field Map SoundSetting =
    [ 0: "enabled", 1: "disabled", 0xFF: "invalid" ]

@Field Map UserCycleSetting =
    [
        0: "Auto Sense",  //"auto",
        1: "Heavy",  //"intense",
        2: "Normal",
        3: "1 Hour Wash", //"delicate",
        4: "Rinse", // "30_mins",
        5: "eco",
        6: "rinse"
    ]

@Field Map UserWashTempSetting =
    [ 0: "normal", 1: "boost", 2: "sanitize", 3: "boost_and_sanitize" ]

@Field Map UserDryOptionSetting =
    [ 0: "off", 1: "power_dry", 2: "max_dry" ]

@Field Map UserWashZoneSetting =
    [ 0: "both", 1: "lower", 2: "upper" ]

@Field DISHWASHER_CYCLE_NAME = "0x301c"
@Field DISHWASHER_CYCLE_STATE = "0x300e"
@Field DISHWASHER_OPERATING_MODE = "0x3001"
@Field DISHWASHER_PODS_REMAINING_VALUE = "0x301f"
@Field DISHWASHER_RINSE_AGENT = "0x3003"
@Field DISHWASHER_USER_SETTING = "0x3007"
@Field DISHWASHER_TIME_REMAINING = "0xd004"
@Field DISHWASHER_UNKNOWN_3009 = "0x3009"  
@Field DISHWASHER_UNKNOWN_301d = "0x301d"
@Field DISHWASHER_UNKNOWN_3035 = "0x3035"
@Field DISHWASHER_DOOR_STATUS = "0x3037"
@Field DISHWASHER_UNKNOWN_3045 = "0x3045"
@Field DISHWASHER_UNKNOWN_304E = "0x304e"
@Field DISHWASHER_UNKNOWN_3100 = "0x3100"   //Pod count value ?
@Field DISHWASHER_UNKNOWN_D003 = "0xd003"
