The bt_helper.py script is a helper script to connect to a Karoo 2 device if 
you do not have a second Karoo available. 

To use the script, you will need to have Python 3 installed on your computer, and the [PyBluez library](https://github.com/pybluez/pybluez) installed

To run the script, open a terminal and navigate to the directory where the script is located. Then run the following command:

```bash
py bt_helper.py server
```
to start a server on your computer, or
```bash
py bt_helper.py client
```
to connect to a server running on another computer.
