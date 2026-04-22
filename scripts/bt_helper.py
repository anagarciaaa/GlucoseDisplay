import bluetooth 
import sys
import struct
import random
import time 

uuid = "1169eaaf-176c-417f-ac07-39101502c738"

def server():
    server_sock = bluetooth.BluetoothSocket(bluetooth.RFCOMM)
    server_sock.bind(("", bluetooth.PORT_ANY))
    server_sock.listen(1)

    port = server_sock.getsockname()[1]


    bluetooth.advertise_service(server_sock, "SampleServer", service_id=uuid,
                                service_classes=[uuid, bluetooth.SERIAL_PORT_CLASS],
                                profiles=[bluetooth.SERIAL_PORT_PROFILE],
                                # protocols=[bluetooth.OBEX_UUID]
                                )

    print("Waiting for connection on RFCOMM channel", port)

    client_sock, client_info = server_sock.accept()
    print("Accepted connection from", client_info)

    try:
        name  = input("Enter a name: ")
        nameLen =  bytes([len(name)])
        nameBytes = bytearray(name, "utf-8")
        client_sock.send(nameLen)
        client_sock.send(nameBytes)
        #rand number 
        
        maxhr = random.uniform(200, 300)
        ss = f"MAX_HEART_RATE:{maxhr};"
        maxpower = random.uniform(200, 300)
        ss += f"FTP:{maxpower};"
        
        # client_sock.send(bytes([len(ss)]))
        # print("Sending: ", ss)
        # client_sock.send(bytearray(ss, "utf-8"))
        wait = 10
        startTime = time.time()
        sendTime = startTime + wait
        sent = False
        while True:
            if time.time() > sendTime and not sent:
                sent = True
                client_sock.send(bytes([len(ss)]))
                print("Sending: ", ss)
                client_sock.send(bytearray(ss, "utf-8"))
            hr = random.uniform(0, 300)
            speed = random.uniform(0, 30)
            power = random.uniform(0, 300)
            n = client_sock.recv(1)
            numBytes = int.from_bytes(n, byteorder='big')
            data = client_sock.recv(numBytes)
            print("Received: ", data)
            s = f"HEART_RATE:{hr};SPEED:{speed};POWER:{power};"
            print("Sending: ", s)
            b = bytearray(s, "utf-8")
            client_sock.send(bytes([len(b)]))
            client_sock.send(b)
            
    except OSError:
        pass

    print("Disconnected.")

    client_sock.close()
    server_sock.close()
    print("All done.")
    
def client():
    addr = None

    if len(sys.argv) < 2:
        print("No device specified. Searching all nearby bluetooth devices for "
            "the SampleServer service...")
    else:
        print("Searching for SampleServer on {}...".format(addr))

    # search for the SampleServer service
    service_matches = bluetooth.find_service(uuid=uuid)

    if len(service_matches) == 0:
        print("Couldn't find the SampleServer service.")
        sys.exit(0)

    first_match = service_matches[0]
    port = first_match["port"]
    name = first_match["name"]
    host = first_match["host"]

    print("Connecting to \"{}\" on {}".format(name, host))

    # Create the client socket
    client_sock = bluetooth.BluetoothSocket(bluetooth.RFCOMM)
    client_sock.connect((host, port))

    print("Connected. Type something...")
    try:
        name  = input("Enter a name: ")
        nameLen =  bytes([len(name)])
        nameBytes = bytearray(name, "utf-8")
        client_sock.send(nameLen)
        client_sock.send(nameBytes)
        #rand number 
        
        maxhr = random.uniform(200, 300)
        ss = f"MAX_HEART_RATE:{maxhr};"
        maxpower = random.uniform(200, 300)
        ss += f"FTP:{maxpower};"
        
        # client_sock.send(bytes([len(ss)]))
        # print("Sending: ", ss)
        # client_sock.send(bytearray(ss, "utf-8"))
        wait = 10
        startTime = time.time()
        sendTime = startTime + wait
        sent = False
        while True:
            if time.time() > sendTime and not sent:
                sent = True
                client_sock.send(bytes([len(ss)]))
                print("Sending: ", ss)
                client_sock.send(bytearray(ss, "utf-8"))
            hr = random.uniform(0, 300)
            speed = random.uniform(0, 30)
            power = random.uniform(0, 300)
            n = client_sock.recv(1)
            numBytes = int.from_bytes(n, byteorder='big')
            data = client_sock.recv(numBytes)
            print("Received: ", data)
            s = f"HEART_RATE:{hr};SPEED:{speed};POWER:{power};"
            print("Sending: ", s)
            b = bytearray(s, "utf-8")
            client_sock.send(bytes([len(b)]))
            client_sock.send(b)
            
    except OSError:
        pass

    client_sock.close()
    
def main():
    if len(sys.argv) < 2:
        print("Usage: {} [server|client]".format(sys.argv[0]))
        sys.exit(2)

    if sys.argv[1] == "server":
        server()
    elif sys.argv[1] == "client":
        client()
    else:
        print("Unknown option: {}".format(sys.argv[1]))
        sys.exit(2)
    
main()