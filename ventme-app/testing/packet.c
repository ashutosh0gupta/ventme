
// call the following function from main
int send_readings( float pressure, float airflow, float volume);


// The following variables are expected to be declared
// in main

// Send buffer
extern unsigned char TxBuf[2000];
extern unsigned TxLen;

// Ventilator Status variables
extern float Oxygen;
extern unsigned short Error_Code,Set_TD;
extern unsigned char Set_Oxy,Set_RR,Set_IE,Set_Peep;

#define SAMPLES_PER_PACKET 20
#define SAMPLE_RATE 100

short sync_counter = 0;
short packet_counter = 0;

int write_char( char c, int idx ) {
  TxBuf[idx] = c;
  return idx + 1;
}

int write_short( short s, int idx ) {
  TxBuf[idx] = (s >> 8) & 0x00FF;
  TxBuf[idx+1] = s & 0x00FF;
  return idx+2;
}

int send_header( int idx ) {
    //===================Packet Header
    TxBuf[idx+0] = 0xAA;
    TxBuf[idx+1] = 0x55;
    TxBuf[idx+2] = 0xAA;
    TxBuf[idx+3] = 0x55;
    //======================Frame Counter
    idx = write_short( packet_counter, idx+4);
    //======================Sampling Rate and number of samples
    idx = write_char( SAMPLE_RATE, idx );
    idx = write_char( SAMPLES_PER_PACKET, idx );
    //======================Error Code
    idx = write_short( Error_Code, idx);
    //=======================Set Parameter
    idx = write_char(  Set_Oxy, idx );
    idx = write_char( Set_Peep, idx );
    idx = write_char(   Set_RR, idx );
    idx = write_short(  Set_TD, idx );
    idx = write_char(   Set_IE, idx );
    //==================for future
    TxBuf[idx+0] = 0x00;
    TxBuf[idx+1] = 0x00;
    TxBuf[idx+2] = 0x00;
    TxBuf[idx+3] = 0x00;

    idx = write_char( (char)Oxygen, idx+4);

    packet_counter++;
    return idx;
}

int send_footer( int idx ) {
  //===================Packet footer
    TxBuf[idx+0] = 0x5A;
    TxBuf[idx+1] = 0x5A;
    TxBuf[idx+2] = 0x5A;
    TxBuf[idx+3] = 0x5A;
    return idx+4;
}

int send_readings( float pressure, float airflow, float volume) {
  int local_idx,idx;
  do{
    // read global
    local_idx = TxLen;

    //Load packet
    idx = local_idx;
    if( sync_counter == 0 )
      idx = send_header(idx);
    idx = write_short( (short)(pressure*100.0), idx );
    idx = write_short( (short)(airflow*100.0), idx );
    idx = write_short( (short)(volume*10.0), idx );
    if( sync_counter == SAMPLES_PER_PACKET-1 )
      idx = send_footer(idx);

    // check if there was an interrupt
  }while( local_idx != TxLen );
  //if no interrupt
  TxLen = idx;
  sync_counter = (sync_counter + 1) % SAMPLES_PER_PACKET;
}
