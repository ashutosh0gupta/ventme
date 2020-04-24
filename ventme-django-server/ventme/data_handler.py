# -- TODOs
#  * Investigate how the min/max will behave if there is a long flat max/min

num_display_samples = 2000
num_maxmin_samples = 8

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
        # tData = self.low_pass[vid]
        # tData = self.diff[vid]
        return pData, aData, tData, self.rr,self.ie_ratio,self.peep,self.pmax

    def compute_rr_ie( self, sample_rate ):
        if self.mm_ctr != num_maxmin_samples -1:
            # run whenever we have new num_maxmin_samples samples
            return
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
        avg_lambdas = sum(lambdas) / len(lambdas)
        avg_ratios = sum(ratios) / len(ratios)
        flipped = False
        if avg_ratios < 1: avg_ratios = 1/avg_ratios
        print(avg_ratios)        
        flipped = True
        ratio_str = None
        for i in range(1,4):
            if  i*0.8 < avg_ratios and avg_ratios < i*1.2:
                ratio_str = str(i)
                break
        if ratio_str:
            if flipped :
                ratio_str = ratio_str + ':1'
            else:
                ratio_str = '1:' + ratio_str
        else:
            ratio_str = "-:-"
        self.rr = int(60*sample_rate/avg_lambdas)
        self.ie_ratio = ratio_str
        
    def put_packet( self, pressure, airflow, volume, sample_rate ):
        lp = 0.98
    
        pctr = (self.ctr-1)% num_display_samples
        for idx in range( 0,len(pressure) ):
            self.pressure[self.ctr] = pressure[idx]
            self.airflow[self.ctr] = airflow[idx]
            self.volume[self.ctr] = volume[idx]

            # Max/Min detection: low_pass -> differentiate -> zero-crossing
            self.low_pass[self.ctr]=lp*self.low_pass[pctr]+(1-lp)*pressure[idx]
            #self.low_pass[self.ctr]=lp*self.low_pass[pctr]+(1-lp)*airflow[idx]
            self.diff[self.ctr]=self.low_pass[self.ctr]-self.low_pass[pctr]
            last_event = abs(self.maxmin[self.mm_ctr])
            if last_event > 0 and last_event-1 == self.ctr:
                # wavelength is too long cannot detect mins/maxes
                # reset all calculations
                self.rr = None
                self.ie_ratio = None
                self.peep = None
                self.mm_ctr = 0
                self.maxmin = [0]*num_maxmin_samples                
            if( self.diff[pctr] <= 0 and self.diff[self.ctr] > 0 ):
                # found a minima at the start of the rising edge
                self.maxmin[self.mm_ctr] = -(self.ctr + 1)
                self.compute_rr_ie( sample_rate )
                self.mm_ctr = (self.mm_ctr + 1) % num_maxmin_samples
            if( self.diff[pctr] >= 0 and self.diff[self.ctr] < 0 ):
                # found a maxima at the start of the falling edge
                self.maxmin[self.mm_ctr] = (self.ctr + 1)
                self.compute_rr_ie( sample_rate )
                self.mm_ctr = (self.mm_ctr + 1) % num_maxmin_samples
            
            pctr = self.ctr
            self.ctr = (self.ctr + 1) % num_display_samples

        if self.ctr >= num_display_samples - len(pressure) :
            self.peep = min(self.pressure[:self.ctr])
            self.pmax = max(self.pressure[:self.ctr])

        # create a gap in the view; place a marker
        self.pressure[self.ctr] = None
        self.airflow[self.ctr] = None
        self.volume[self.ctr] = None
        self.low_pass[self.ctr] = None
        self.diff[self.ctr] = None
    
