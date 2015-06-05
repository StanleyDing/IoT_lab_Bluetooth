#include <SoftwareSerial.h>


SoftwareSerial BT(10, 11); // RX, TX
char val;
boolean ledOn;

void setup()  
{
  // Open serial communications and wait for port to open:
  Serial.begin(9600);
  //LED port
  pinMode(13, OUTPUT);

  // set the data rate for the SoftwareSerial port
  BT.begin(9600);
  
  ledOn = false;
  BT.print("Off");
}

void loop() // run over and over
{ 
  if (Serial.available()) {
    val = Serial.read();
    BT.print(val);
  }
  
  if (BT.available()) {
    val = BT.read();
    Serial.print(val);
    
    if (val == 'O') {
      digitalWrite(13, HIGH);
      ledOn = true;
      BT.print("On");
    } else if (val == 'X') {
      digitalWrite(13, LOW);
      ledOn = false;
      BT.print("Off");
    } else if (val == '?') {
      if (ledOn) BT.print("On");
      else BT.print("Off");
    }
  }
}


