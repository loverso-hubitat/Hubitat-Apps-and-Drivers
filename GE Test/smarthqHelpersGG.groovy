library (
    author: "tomw",
    category: "",
    description: "SmartHQ helpers",
    name: "smarthqHelpers",
    namespace: "tomw",
    documentationLink: ""
)

import groovy.transform.Field

@Field client_id        = "564c31616c4f7474434b307435412b4d2f6e7672"
@Field client_secret    = "6476512b5246446d452f697154444941387052645938466e5671746e5847593d"
@Field redirect_uri     = "brillion.4e617a766474657344444e562b5935566e51324a://oauth/redirect"
@Field cookie1          = "abgea_region=us-east-1; Domain=accounts.brillion.geappliances.com; HttpOnly; Path=/; Secure"

@Field loginUrl         = "https://accounts.brillion.geappliances.com"
@Field apiUrl           = "https://api.brillion.geappliances.com"

@Field doorNormallyOpen      =  0x0001
@Field doorNormallyClosed    =  0x0002

def logDebug(msg)
{
    if (logEnable || parent?.isLogEnable())
    {
        log.debug(msg)
    }
}

def setupDevDetails(details)
{
    if(!details)
    {
        return
    }
    
    device.setLabel(details.label)
    device.setName(details.devType)
    
    device.updateDataValue("mac", details.mac)
}

def push(number = 0)
{
    sendEvent(name: "pushed", value: number)
}

def decodeErdString(hexStr)
{
    return new String(hubitat.helper.HexUtils.hexStringToByteArray(hexStr))
}

def decodeErdInt(byte[] val)
{
    return hubitat.helper.HexUtils.hexStringToInt(hubitat.helper.HexUtils.byteArrayToHexString(val))
}

def encodeErdInt(val, minBytes)
{
    return hubitat.helper.HexUtils.integerToHexString(val, minBytes)
}

def decodeErdSignedByte(byte val)
{
    def intVal = decodeErdInt(val)
    
    return (intVal <= 128) ? intVal : (intVal - 256) 
}

def encodeErdBool(boolean val)
{
    def intVal = (true == val) ? 1 : 0
    return encodeErdInt(intVal, 1)
}

def decodeErdBool(byte val)
{
    return (val == 0x00) ? false : true
}

def encodeErdSignedByte(val)
{
    val = (val < 0) ? (val + 256) : val
    return hubitat.helper.HexUtils.integerToHexString(val, 1)
}

def refreshAppliance()
{
    parent?.request_update(buildDevDetails()?.mac)
}

def getUserId()
{
    return parent?.getDataValue("userId") ?: getDataValue("userId")
}

def hostPath()
{
    apiUrl.split("//")?.getAt(1)    
}

def buildDevDetails()
{
    def details =
        [
            userId: getUserId(),
            mac: device.getDataValue("mac"),
            host: hostPath()
        ]
    
    return details
}

def buildErdDetails(code, value)
{
    def details = 
        [
            code: code,
            value: value
        ]
    
    return details
}

def buildWssMsg(method, path, id, body = null)
{
    def msg =
        [
            kind: "websocket#api",
            action: "api",
            host: hostPath(),
            method: method,
            path: path,
            id: id
        ]
    
    if(body)
    {
        msg.put("body", body)
    }

    return msg
}

def buildErdSetter(devDetails, erdDetails)
{
    if([devDetails, erdDetails].contains(null))
    {
        return
    }
    
    def erdCodeString = erdDetails.code?.toUpperCase()?.replace("0X", "0x")
    
    def body =
        [
            kind: "appliance#erdListEntry",
            userId: devDetails.userId,
            applianceId: devDetails.mac,
            erd: erdCodeString,
            value: erdDetails.value,
            ackTimeout: 10,
            delay: 0
        ]
    
    def path = "/v1/appliance/${devDetails.mac}/erd/${erdCodeString}"
    def id = "${devDetails.mac}-setErd-${erdCodeString}"
    def message = buildWssMsg("POST", path, id, body)
    
    return message
}


///send commands//

def startPost(user,mac){
    def body =
        [
            kind: "appliance#control",
            userId: "$user",
            applianceId: "$mac",
            command: "dishwasher-start",
            data:[],
            ackTimeout: 10,
            delay: 0
        ]
    def path = "/v1/appliance/"+"$mac"+"/control/dishwasher-start"
    def id = ""
    def message = buildWssMsg("POST", path , id,  body)
    
    return message
}

def stopPost(user,mac){
    def body =
        [
            kind: "appliance#control",
            userId: "$user",
            applianceId: "$mac",
            command: "dishwasher-stop",
            data:[],
            ackTimeout: 10,
            delay: 0
        ]
    def path = "/v1/appliance/"+"$mac"+"/control/dishwasher-stop"
    def id = ""
    def message = buildWssMsg("POST", path , id,  body)
    
    return message
}


private subBytes(arr, start, length)
{
    return arr.toList().subList(start, start + length) as byte[]
}

def flushEvents(events)
{
    events.each
    {
        sendEvent(it)
    }  
}

def parseDoorStatusByte(value, type)
{
    try
    {
        def bytes = hubitat.helper.HexUtils.hexStringToByteArray(value)
        if(1 != bytes.size())
        {
            return
        }
        
        def val = "NA"
        def nClosed = (type == doorNormallyClosed)
        
        val = nClosed ? doorNCStatus(bytes[0]) : doorNOStatus(bytes[0])
        
        def events = [[:]] 
        events += [name: "contact", value: val]
        events += [name: "doorStatus", value: val]
        
        flushEvents(events)
    }
    catch (Exception e)
    {
        logDebug("parseDoorStatusByte: ${e.message}")
    }
}

def doorNCStatus(val)
{
    switch(val)
    {
        case 0x01:
            return "open"
        case 0x00:
            return "closed"
        default:
            return "NA"
    }    
}

def doorNOStatus(val)
{
    switch(val)
    {
        case 0x00:
            return "open"
        case 0x01:
            return "closed"
        default:
            return "NA"
    }    
}

def setBooleanErd(erd, boolean value)
{
    def strVal = encodeErdBool(value)
    def erdMap = buildErdSetter(buildDevDetails(), buildErdDetails(erd, strVal))
    
    parent?.sendWssMap(erdMap)
}

def setSabbathMode(value)
{
    def boolVal = (value == "true")
    setBooleanErd(SABBATH_MODE, boolVal)
}

def setSoundLevel(value)
{
    def boolVal = (value == "true")
    setBooleanErd(SOUND_LEVEL, boolVal)
}

def setControlLock(value)
{
    def boolVal = (value == "true")
    setBooleanErd(USER_INTERFACE_LOCKED, boolVal)
}

def setIntegerErd(val, options, erd)
{
    def intVal = options.find {val == it.value}?.key?.toInteger()
    
    if(null == intVal)
    {
        // unsupported value sent, so discarding
        log.debug "invalid setting (${val})"
        return
    }
    
    def erdVal = encodeErdInt(intVal, 1)
    def erdMap = buildErdSetter(buildDevDetails(), buildErdDetails(erd, erdVal))
    
    parent?.sendWssMap(erdMap)     
}

@Field APPLIANCE_TYPE = "0x0008"
@Field CLOCK_FORMAT = "0x0006"
@Field CLOCK_TIME = "0x0005"
@Field MODEL_NUMBER = "0x0001"
@Field SABBATH_MODE = "0x0009"
@Field SERIAL_NUMBER = "0x0002"
@Field SOUND_LEVEL = "0x000a"
@Field TEMPERATURE_UNIT = "0x0007"
@Field USER_INTERFACE_LOCKED = "0x0004"
@Field UNIT_TYPE = "0x0035"
