#include <errno.h>
#include <stdio.h>
#include <fcntl.h> 
#include <string.h>
#include <termios.h>
#include <unistd.h>

int
set_interface_attribs (int fd, int speed, int parity)
{
        struct termios tty;
        if (tcgetattr (fd, &tty) != 0)
        {
                printf("error %d from tcgetattr", errno);
                return -1;
        }

        cfsetospeed (&tty, speed);
        cfsetispeed (&tty, speed);

        tty.c_cflag = (tty.c_cflag & ~CSIZE) | CS8;     // 8-bit chars
        // disable IGNBRK for mismatched speed tests; otherwise receive break
        // as \000 chars
        tty.c_iflag &= ~IGNBRK;         // disable break processing
        tty.c_lflag = 0;                // no signaling chars, no echo,
                                        // no canonical processing
        tty.c_oflag = 0;                // no remapping, no delays
        tty.c_cc[VMIN]  = 0;            // read doesn't block
        tty.c_cc[VTIME] = 5;            // 0.5 seconds read timeout

        tty.c_iflag &= ~(IXON | IXOFF | IXANY); // shut off xon/xoff ctrl

        tty.c_cflag |= (CLOCAL | CREAD);// ignore modem controls,
                                        // enable reading
        tty.c_cflag &= ~(PARENB | PARODD);      // shut off parity
        tty.c_cflag |= parity;
        tty.c_cflag &= ~CSTOPB;
        tty.c_cflag &= ~CRTSCTS;

        if (tcsetattr (fd, TCSANOW, &tty) != 0)
        {
                printf ("error %d from tcsetattr", errno);
                return -1;
        }
        return 0;
}

void
set_blocking (int fd, int should_block)
{
        struct termios tty;
        memset (&tty, 0, sizeof tty);
        if (tcgetattr (fd, &tty) != 0)
        {
                printf ("error %d from tggetattr", errno);
                return;
        }

        tty.c_cc[VMIN]  = should_block ? 1 : 0;
        tty.c_cc[VTIME] = 5;            // 0.5 seconds read timeout

        if (tcsetattr (fd, TCSANOW, &tty) != 0)
                printf ("error %d setting term attributes", errno);
}


unsigned char msg[2000];
short packet_count = 1;

int write_char( char c, int idx ) {
  char * write_position = (msg+idx);
  *write_position = c;
  return idx + 1;
}

int write_short( short s, int idx ) {
  idx = write_char( (s >> 8) & 0x00FF, idx );
  idx = write_char( s & 0x00FF, idx );
  /* short * write_position = (short*)(msg+idx); */
  /* *write_position = s; */
  return idx;
}

int write_int( int s, int idx ) {
  idx = write_char( (s >> 24) & 0x00FF, idx );
  idx = write_char( (s >> 16) & 0x00FF, idx );
  idx = write_char( (s >> 8) & 0x00FF, idx );
  idx = write_char( s & 0x00FF, idx );
  return idx;
  /* int * write_position = (int*)(msg+idx); */
  /* *write_position = s; */
  /* return idx + 4; */
}

void sendPacket( int fd, short* pressure, short* flow, short* tidal, int size, int oxygen ){
  short errorCode = 30;
  char setOxygen = 10;
  short setPeep = 20;
  char setRR = 7;
  short setTidal = 350;
  char setIE = 2;

  msg[0] = 0xAA;
  msg[1] = 0x55;
  msg[2] = 0xAA;
  msg[3] = 0x55;
  int idx = 4;

  idx = write_short( packet_count, idx );
  idx = write_char( 100, idx );
  idx = write_char( size, idx );
  idx = write_short( errorCode, idx );

  idx = write_char( setOxygen, idx );
  idx = write_short(  setPeep, idx );
  idx = write_char(     setRR, idx );
  idx = write_short( setTidal, idx );
  idx = write_char(     setIE, idx );

  //padding
  idx = write_char( 'a', idx );
  idx = write_char( 'b', idx );
  idx = write_char( 'c', idx );

  //assert( idx == 20 );

  idx = write_char( oxygen, idx );
  for( unsigned i =0 ; i < size; i++ ) {
    idx = write_short( pressure[i], idx );
    idx = write_short( flow[i], idx );
    idx = write_short( tidal[i], idx );
  }

  //adding a footer
  msg[idx+0] = 0x5A;
  msg[idx+1] = 0x5A;
  msg[idx+2] = 0x5A;
  msg[idx+3] = 0x5A;
  idx = idx + 4;

  printf("%d\n", packet_count);
  /* for (int i = 0; i < idx; i ++) { */
  /*   if(i % 20 == 0 ) */
  /*     printf("\n"); */
  /*   printf("%02x", msg[i]); */
  /* } */
  /* printf("\n"); */


  write(fd, msg, idx);

  //printf("%d", *(msg+4));

  packet_count = packet_count + 1;
}


void main() {


  char *portname = "/dev/ttyUSB0";

  int fd = open (portname, O_RDWR | O_NOCTTY | O_SYNC);
  if (fd < 0) {
    printf("error %d opening %s: %s", errno, portname, strerror (errno));
    return;
  }
  //B115200
  set_interface_attribs (fd, B9600 , 0);  // set speed to 115,200 bps, 8n1 (no parity)
  set_blocking (fd, 0);                // set no blocking

  /* msg[0] = 0xAA; */
  /* msg[1] = 0x55; */
  /* msg[2] = 0xAA; */
  /* msg[3] = 0x55; */

  /* for( unsigned i = 4; i < 20; i++ ) { */
  /*   msg[i] = 'a'; */
  /* } */


  FILE *in_file  = fopen("./raw_data.txt", "r");
   if (! in_file ) {
     printf("oops, file can't be read\n");
     return;
   }

  short pressure[20];
  short flow[20];
  short tidal[20];
  short oxygen = 45;

  for(unsigned i = 0; i < 1000; i++ ) {
    for( unsigned j = 0; j < 20; j++ ) {
      float p,f;
      if(fscanf( in_file, "%f\t%f\n", &p, &f) != 2) {
        printf("We are here!");
        return;
      }
      if( j % 2 == 0 ) {
        pressure[j>>1] = (short)(p*100);
        flow[j>>1] = (short)(f*100);
      }
    }
    sendPacket( fd, pressure, flow, tidal, 10, oxygen );
    usleep ( 2*10 *100 * 100);             // sleep enough to transmit the 7 plus
  }
  //sendPacket( fd, pressure, flow, tidal, 10, oxygen );

  /* write (fd, msg, 20);           // send 7 character greeting */

  // receive 25:  approx 100 uS per char transmit
  //char buf [100];
  //int n = read (fd, buf, sizeof buf);  // read up to 100 characters if ready to read
}
