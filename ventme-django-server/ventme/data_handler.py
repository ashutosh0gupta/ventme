# -- TODOs
#  * Investigate how the min/max will behave if there is a long flat max/min
#  * Improve I/E detection

num_display_samples = 2000
num_maxmin_samples = 8
min_pressure_change = 10

class Ventilator_data:
    # incomming data
    ctr = 0
    pressure = [0]*num_display_samples
    airflow  = [0]*num_display_samples
    volume   = [0]*num_display_samples

    # intermediate derived data
    low_pass = [0]*num_display_samples
    diff = [0]*num_display_samples
    mm_ctr = 0
    maxmin = [0]*num_maxmin_samples

    total_sum = 0
    running_size = 50
    rv_ctr = 0
    running_vals = [0]*running_size
    
    # derived output
    rr = None
    peep = None
    pmax = None
    ie_ratio = None

    def reset( self ):
        self.ctr = 0
        self.pressure = [0]*num_display_samples
        self.airflow  = [0]*num_display_samples
        self.volume   = [0]*num_display_samples
        self.mm_ctr = 0
        self.maxmin = [0]*num_maxmin_samples
        self.rr = None
        self.peep = None
        self.pmax = None
        self.ie_ratio = None

    def get_display( self ):
        pData   = self.pressure
        aData   = self.airflow
        tData   = self.volume
        # aData = self.low_pass
        # tData = self.diff
        return pData, aData, tData, self.rr,self.ie_ratio,self.peep,self.pmax

    def reset_internal_state(self):
        print('Internal reset called?')
        self.rr = None
        self.ie_ratio = None
        # self.peep = None
        # self.pmax = None
        self.mm_ctr = 0
        self.maxmin = [0]*num_maxmin_samples                

    def compute_rr_ie( self, found_mm, sample_rate ):

        # collect min/max
        prev_mm_ctr = (self.mm_ctr-1) % num_maxmin_samples
        if( (found_mm < 0) and (self.maxmin[prev_mm_ctr] < 0) ):
            return
        if( (found_mm > 0) and (self.maxmin[prev_mm_ctr] > 0) ):
            return
        self.maxmin[self.mm_ctr] = found_mm
        self.mm_ctr = (self.mm_ctr + 1) % num_maxmin_samples
        if self.mm_ctr != 0 : # num_maxmin_samples -1:
            return
        # print(self.maxmin)
 
        # run whenever we have new num_maxmin_samples samples
        # minima points are stored as negative numbers
        # and maxima points are stored as positive numbers
        lambdas = []
        ratios = []
        last_inhale = None
        last_exhale = None
        isMin = (self.maxmin[0] > 0)
        for i in range(1,num_maxmin_samples):
            if( isMin == (self.maxmin[i] > 0) ):
                # min max are not alternating
                self.reset_internal_state()
                return
            diff = self.maxmin[i]+self.maxmin[i-1]
            if isMin: diff = -diff
            diff = diff % num_display_samples
            if isMin:
                last_exhale = diff
            else:
                last_inhale = diff
            if last_inhale != None and last_exhale != None and isMin:
                lambdas.append(last_inhale+last_exhale)
                ratios.append( last_inhale/last_exhale )
            isMin = not isMin

        # lambdas - wave lengths
        # ratios - I:E ratios
        avg_lambdas = sum(lambdas) / len(lambdas)
        avg_ratios = sum(ratios) / len(ratios)
        # print(lambdas)
        # print(ratios)
        flipped = (avg_ratios < 1)
        if avg_ratios < 1: avg_ratios = 1/avg_ratios
        ratio_str = "{:.1f}".format(avg_ratios)
        for i in range(1,4):
            if  i*0.8 < avg_ratios and avg_ratios < i*1.2:
                ratio_str = str(i)
                break
        if flipped :
            ratio_str = ratio_str + ':1'
        else:
            ratio_str = '1:' + ratio_str
        self.rr = int(60*sample_rate/avg_lambdas)
        self.ie_ratio = ratio_str

    def val_band( self, val, ma, mi ):
        if ma == None: return 0
        max_diff = ma-mi
        if( max_diff > min_pressure_change ):
            if val > self.pmax- max_diff/10:
                return 1
            elif val < self.peep+ max_diff/10:
                return -1
        return 0

    def put_packet( self, pressure, airflow, volume, sample_rate ):

        # Coeffcient for the lowpass filter
        lp = 0.98
    
        pctr = (self.ctr-1)% num_display_samples
        nctr = (self.ctr+1)% num_display_samples
        for idx in range( 0,len(pressure) ):
            self.pressure[self.ctr] = pressure[idx]
            self.airflow[self.ctr] = airflow[idx]
            self.volume[self.ctr] = volume[idx]
            #
            # Max/Min detection: low_pass -> differentiate -> zero-crossing
            #
            #self.low_pass[self.ctr]=lp*self.low_pass[pctr]+(1-lp)*pressure[idx]
            # method 1 for lowpass
            #self.low_pass[self.ctr]=lp*self.low_pass[pctr]+(1-lp)*airflow[idx]
            # method 2 for lowpass
            self.running_vals[self.rv_ctr] = self.pressure[self.ctr]
            self.rv_ctr = (self.rv_ctr + 1) % self.running_size
            self.low_pass[self.ctr]= sum(self.running_vals)/self.running_size
            #method 3 for lowpass
            bp = self.val_band(self.low_pass[self.ctr],self.pmax,self.peep)
            self.low_pass[self.ctr] = bp
                        
            self.diff[self.ctr]=self.low_pass[self.ctr]-self.low_pass[pctr]

            prev_mm_ctr = (self.mm_ctr-1) % num_maxmin_samples
            last_event = abs(self.maxmin[prev_mm_ctr])
            if last_event > 0 and last_event-1 == self.ctr:
                # wavelength is too long cannot detect mins/maxes
                # reset all calculations
               self.reset_internal_state()
            if( self.diff[pctr] <= 0 and self.diff[self.ctr] > 0 ):
                # found a minima at the start of the rising edge
                self.compute_rr_ie( -(self.ctr + 1), sample_rate )
                # self.mm_ctr = (self.mm_ctr + 1) % num_maxmin_samples
            if( self.diff[pctr] >= 0 and self.diff[self.ctr] < 0 ):
                # found a maxima at the start of the falling edge
                self.compute_rr_ie( (self.ctr + 1), sample_rate )
                # self.mm_ctr = (self.mm_ctr + 1) % num_maxmin_samples

            pctr = self.ctr
            self.ctr = (self.ctr + 1) % num_display_samples
            nctr = (nctr + 1) % num_display_samples
            
        # periodically update PEEP and PMAX
        if self.ctr >= num_display_samples - len(pressure) :
            self.peep = int(min(self.pressure[:self.ctr]))
            self.pmax = int(max(self.pressure[:self.ctr]))

        # create a gap in the view; place a marker
        self.pressure[self.ctr] = None
        self.airflow[self.ctr] = None
        self.volume[self.ctr] = None
        self.low_pass[self.ctr] = None
        self.diff[self.ctr] = None
    
