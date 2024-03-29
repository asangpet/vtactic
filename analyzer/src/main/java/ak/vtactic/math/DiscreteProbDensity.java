package ak.vtactic.math;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiscreteProbDensity {
	Logger log = LoggerFactory.getLogger(DiscreteProbDensity.class);

	double min;
	double max;
	double interval;
	double offset = 0;
	double[] pdf;
	int numSlots;
	Long rawCount = Long.valueOf(0L);
	double[] raw = null;
	ArrayList<Double> series = new ArrayList<Double>(20000);

	public DiscreteProbDensity() {
		this(ModelConfig.slots,0,ModelConfig.maxTime,ModelConfig.offset);
	}
	
	public DiscreteProbDensity(int numSlots, double min, double max, double offset) {
		this.numSlots = numSlots;
		this.min = min;
		this.max = max;
		this.interval = (max-min)/numSlots;
		this.offset = offset;
		pdf = new double[numSlots];
	}

	public DiscreteProbDensity(DiscreteProbDensity origin) {
		this.numSlots = origin.numSlots;
		this.min = origin.min;
		this.max = origin.max;
		this.interval = origin.interval;
		this.rawCount = origin.rawCount;
		this.offset = origin.offset;
		this.pdf = origin.pdf.clone();
	}

	public void add(double value) {
		int slot = (int)Math.round((value-min) / interval);
		// bounded slot
		if (slot >= pdf.length || slot < 0) slot = pdf.length-1;
		pdf[slot]++;
		rawCount++;
		series.add(value);
	}
	
	public ArrayList<Double> getSeries() {
		return series;
	}
	
	public void convert(double[] value) {
		for (int i=0;i<value.length;i++) {
			int slot = (int)Math.round((value[i]-min) / interval);
			// 	bounded slot
			if (slot >= numSlots) slot = numSlots-1;
			pdf[slot]++;
		}
		rawCount = Long.valueOf(value.length);
		raw = value;
		doNormalize();
	}

	/**
	 * Adding pdf (convolution) assuming this distribution and b has the same interval
	 * @param b
	 * @return
	 */
	public DiscreteProbDensity conv(DiscreteProbDensity b) {
		DiscreteProbDensity result = new DiscreteProbDensity(this.numSlots + b.numSlots, this.min+b.min, this.max+b.max,this.offset);
		for (int x=0;x<result.numSlots;x++) {
			double sum = 0;
			int stop = (x<this.numSlots)?x:this.numSlots-1;
			int start = (x<this.numSlots)?0:x-this.numSlots+1;
			for (int n = start; n<=stop; n++) {
				sum += this.pdf[x-n] * b.pdf[n];
			}
			result.pdf[x] = sum;
		}
		return result;
	}

	public DiscreteProbDensity deconv(DiscreteProbDensity b) {
		DiscreteProbDensity result = new DiscreteProbDensity(this.numSlots, this.min, this.max, this.offset);
		int startB = 0;
		// shift known vector (must start with nonzero element)
		while (b.pdf[startB] <= 0) startB++;
		double divider = b.pdf[startB];
		double[] dummy = new double[result.numSlots*2];

		//System.out.println("divider:"+divider+" startB:"+startB);
		for (int x=0;x<result.numSlots;x++) {
			//System.out.print("x["+x+"]: ");
			int startIndex = x-1;
			double knownSum = 0;
			for (int i=startB+1;i<b.numSlots;i++) {
				//System.out.print("r"+(startIndex)+".b"+i+" ");
				if (startIndex >= 0 && startIndex < dummy.length) {
					knownSum += dummy[startIndex] * b.pdf[i];
				}
				startIndex--;
			}
			//System.out.print("sum:"+knownSum);
			dummy[x] = (this.pdf[x] - knownSum) / divider;
			if (dummy[x] < 0) dummy[x] = 0;
			//System.out.print(" dummy["+(x)+"]:"+dummy[x]);
			//System.out.println();
		}

		for (int x=0;x<result.numSlots;x++) {
			result.pdf[x] = dummy[x+startB];
		}
		return result;
	}

	public DiscreteProbDensity deconv2(DiscreteProbDensity b) {
		DiscreteProbDensity result = new DiscreteProbDensity(this.numSlots, this.min, this.max, this.offset);
		int startB = 0;
		// shift known vector (must start with nonzero element)
		while (b.pdf[startB] <= 0) startB++;
		double divider = b.pdf[startB];
		double[] dummy = new double[result.numSlots*2];

		//System.out.println("divider:"+divider+" startB:"+startB);
		for (int x=0;x<result.numSlots;x++) {
			//System.out.print("x["+x+"]: ");
			dummy[x] = b.pdf[x];
			int lower = Math.max(x - b.numSlots+1, 0);
			for (int i=lower; i<x; i++) {
				dummy[x] -= dummy[i] * pdf[x-i];
			}
			dummy[x] /= divider;
			if (dummy[x] < 0) dummy[x] = 0;
			//if (dummy[x] < 0) dummy[x] = 0;
		}

		for (int x=0;x<result.numSlots;x++) {
			result.pdf[x] = dummy[x+startB];
		}
		return result;
	}

	public DiscreteProbDensity maxPdf(DiscreteProbDensity b) {
		DiscreteCumuDensity acdf = new DiscreteCumuDensity(this);
		DiscreteCumuDensity bcdf = new DiscreteCumuDensity(b);
		
		DiscreteProbDensity result = new DiscreteProbDensity(this.numSlots, this.min, this.max,this.offset);
		for (int x = 0; x<numSlots; x++) {
			result.pdf[x] = pdf[x] * bcdf.pdf[x] + b.pdf[x]*acdf.pdf[x] - pdf[x]*b.pdf[x];
		}
		return result.normalize();
	}
	
	public DiscreteProbDensity randomDeconv(DiscreteProbDensity b) {
		DiscreteProbDensity result = new DiscreteProbDensity(this.numSlots, this.min, this.max,this.offset);
		result.raw = null;
		DiscreteProbDensity bestResult = null;
		double bestError = Double.MAX_VALUE;

		for (int iter=0;iter<10000000;iter++) {
			for (int idx = 0; idx < result.numSlots; idx ++) {
				result.pdf[idx] = Math.random()*max;
			}
			DiscreteProbDensity convolve = tconv(result);
			//find error
			double err = 0;
			for (int idx = 0; idx < numSlots; idx++) {
				err += (convolve.pdf[idx] - pdf[idx])*(convolve.pdf[idx] - pdf[idx]);
			}
			if (err < bestError) {
				bestResult = new DiscreteProbDensity(result);
				bestError = err;
			}
			if (iter % 1000 == 0) System.out.println(bestError);
		}
		return bestResult;
	}

	/**
	 * Truncated convolution
	 * @param b
	 * @return
	 */
	public DiscreteProbDensity tconv(DiscreteProbDensity b) {
		DiscreteProbDensity result = new DiscreteProbDensity(this.numSlots, this.min, this.max,this.offset);
		result.raw = null;
		for (int x=0;x<result.numSlots;x++) {
			double sum = 0;
			int stop = (x<this.numSlots)?x:this.numSlots-1;
			int start = (x<this.numSlots)?0:x-this.numSlots+1;
			for (int n = start; n<=stop; n++) {
				sum += this.pdf[x-n] * b.pdf[n];
			}
			result.pdf[x] = sum;
		}

		// force left shift by the offset amount, to adjust the offset
		int convOffset = (int)Math.round(offset/interval);
		for (int x=0;x<result.numSlots - convOffset; x++) {
			result.pdf[x] = result.pdf[x+convOffset];
		}
		for (int x=result.numSlots-convOffset;x<result.numSlots;x++) {
			result.pdf[x] = 0;
		}
		return result;
	}

	/**
	 * Subtracting pdf (covariance) measure a cross-relation between two distributions
	 * @param b
	 * @return
	 */
	public DiscreteProbDensity cov(DiscreteProbDensity b) {
		DiscreteProbDensity result = new DiscreteProbDensity(this.numSlots + b.numSlots, this.min+b.min, this.max+b.max,this.offset);
		for (int x=0;x<result.numSlots;x++) {
			double sum = 0;
			int start = x-b.numSlots+1;
			for (int n = 0; n < this.numSlots; n++) {
				if (n < b.numSlots && n+start > 0 && n+start<this.numSlots)
					sum += this.pdf[n+start] * b.pdf[n];
			}
			result.pdf[x] = sum;
		}
		return result;
	}

	/**
	 * Subtracting pdf & truncated (covariance) measure a cross-relation between two distributions
	 * @param b
	 * @return
	 */
	public DiscreteProbDensity tcov(DiscreteProbDensity b) {
		DiscreteProbDensity result = new DiscreteProbDensity(this.numSlots, this.min, this.max, this.offset);
		for (int x=0;x<result.numSlots;x++) {
			double sum = 0;
			int start = x-b.numSlots+1;
			for (int n = 0; n < this.numSlots; n++) {
				if (n < b.numSlots && n+start > 0 && n+start<this.numSlots)
					sum += this.pdf[n+start] * b.pdf[n];
			}
			result.pdf[x] = sum;
		}
		return result;
	}

	/**
	 * Perform normalized pdf max algebra (convert to cdf and take min)
	 * @param b
	 * @return
	 */
	public DiscreteProbDensity normMax(DiscreteProbDensity b) {
		return (toCdf().min(b.toCdf()).toPdf()).normalize();
	}

	public DiscreteCumuDensity toCdf() {
		return new DiscreteCumuDensity(this);
	}

	public long count() {
		double freqCount = 0;
		for (int i=0;i<numSlots;i++) {
			freqCount += pdf[i];
		}
		return Math.round(freqCount);
	}

	public void doNormalize() {
		double freqCount = 0;

		for (int i=0;i<numSlots;i++) {
			freqCount += pdf[i];
		}
		if (freqCount > 0) {
			for (int i=0;i<numSlots;i++) {
				pdf[i] = pdf[i]/freqCount;
			}
		}
	}

	// cut off value above given cutpoint
	public DiscreteProbDensity cutoff(double cutpoint) {
		double val = min+interval/2 - offset;
		//double minval = min+interval/2;
		for (int i=0;i<numSlots;i++) {
			if (val > cutpoint) {
				pdf[i] = pdf[i] * Math.exp((cutpoint-val)/interval);
			}
			val += interval;
		}
		doNormalize();
		return this;
	}

	public DiscreteProbDensity cutoff() {
		double mean = average();
		double sd = stdev();
		return cutoff(mean + 5*sd);
		//return cutoff(10.0);
	}

	/**
	 * Return truncated normalized convolution with the given signal
	 * 
	 * @param b
	 * @return
	 */
	public DiscreteProbDensity tnormConv(DiscreteProbDensity b) {
		DiscreteProbDensity result = tconv(b);
		result.doNormalize();
		return result;
	}

	public DiscreteProbDensity normalize() {
		DiscreteProbDensity result = new DiscreteProbDensity(this);
		double freqCount = 0;

		for (int i=0;i<numSlots;i++) {
			freqCount += pdf[i];
		}
		if (freqCount > 0) {
			for (int i=0;i<numSlots;i++) {
				result.pdf[i] = pdf[i]/freqCount;
			}
		}
		return result;
	}

	public double average() {
		double freqCount = 0;
		double sum = 0;
		double val = min+interval/2 - offset;
		double minval = min+interval/2;
		for (int i=0;i<numSlots;i++) {
			freqCount += pdf[i];
			if (val > 0)
				sum += pdf[i]*val;
			else
				sum += pdf[i]*minval;
			val += interval;
		}
		return sum/freqCount;
	}

	public double mode() {
		double maxprob = -1;
		double maxvalue = min;
		double val = min+interval/2 - offset;
		double minval = min+interval/2;
		for (int i=0;i<numSlots;i++) {
			if (pdf[i] > maxprob) {
				maxprob = pdf[i];
				maxvalue = val;
			}
			val += interval;
		}
		if (maxvalue < minval) maxvalue = minval;
		return maxvalue;
	}

	public double stdev() {
		double avg = average();

		double freqCount = 0;
		double sum = 0;
		double val = min+interval/2 - offset;
		double minval = min+interval/2;
		for (int i=0;i<numSlots;i++) {
			freqCount += pdf[i];
			if (val > 0)
				sum += pdf[i]*(val-avg)*(val-avg);
			else
				sum += pdf[i]*(minval-avg)*(minval-avg);
			val += interval;
		}
		return Math.sqrt(sum/freqCount);
	}

	@Override
	public String toString() {
		return "(pdf avg:"+average()+" stdev:"+stdev()+" mode:"+mode()+")";
	}

	/**
	 * Calculate value at the given percentile (for normalized pdf only)
	 * @param percent
	 *
	 * @return percentile rank
	 */
	public double percentile(double percent) {
		double sum = 0;
		double target = percent/100;

		for (int i=0;i<numSlots;i++) {
			sum += pdf[i];
			if (sum >= target) {
				// do linear adjustment
				//return interval*(i + (target - (sum-pdf[i])) / pdf[i]) - offset;
				return i;
			}
		}
		return numSlots-1;
		//return numSlots * interval - offset;
	}

	public int percentileIndex(double percent) {
		double sum = 0;
		double target = percent/100;

		for (int i=0;i<numSlots;i++) {
			sum += pdf[i];
			if (sum >= target) {
				return i;
			}
		}
		return numSlots-1;
		//return numSlots * interval - offset;
	}
	
	public void print() {
		System.out.print("[");
		for (int i=0;i<pdf.length;i++) {
			//System.out.println((min+i*interval)+":"+pdf[i]);
			System.out.print(pdf[i]+" ");
		}
		System.out.println("];");
	}
	
	public void printBuilder(StringBuilder builder) {
		builder.append("[");
		for (int i=0;i<pdf.length;i++) {
			//System.out.println((min+i*interval)+":"+pdf[i]);
			builder.append(pdf[i]+" ");
		}
		builder.append("];");
	}
	
	public StringBuffer printBuffer() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("[");
		// Skip last point (which represents the long tail and can be high)
		for (int i=0;i<pdf.length-1;i++) {
			buffer.append(pdf[i]);
			if (i<pdf.length-2) {
				buffer.append(",");
			}
		}
		buffer.append("]");
		return buffer;
	}
	
	public StringBuffer printSeriesBuffer() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("[");
		int counter = 0;
		for (Double value : series) {
			if (counter > 0) {
				buffer.append(",");
			}
			counter++;
			buffer.append(value);
		}
		buffer.append("]");
		return buffer;
	}
	
	/**
	 * Skewed convolution, we find the probability that a request will takes x,
	 * given there are n co-arrivals between the period of 0 to x.
	 * 
	 * @param n
	 * @param arrivalProb
	 * @param processing
	 * @return
	 */
	public static DiscreteProbDensity coConv(int n, DiscreteProbDensity arrivalProb, DiscreteProbDensity processing) {
		DiscreteProbDensity result = new DiscreteProbDensity(processing);
		for (int x=0;x<result.getPdf().length;x++) {
			double sum = 0;
			for (int k=0;k<x;k++) {
				sum += arrivalProb.pdf[k] * processing.pdf[(x+k)/n];
			}
			result.pdf[x] = sum;
		}
		return result;
	}

	public DiscreteProbDensity ensurePositive() {
		double sum = 0.0;
		for (int i=0;i<pdf.length;i++) {
			if (pdf[i]<0) pdf[i]=0;
			sum+=pdf[i];
		}
		if (sum <= 0) {
			log.debug("***** PDF smoothing no sum: set peak at 0 / sum {} to 1.0",sum);
			sum = 1.0;
			pdf[0] = 0.999;
			pdf[1] = 0.001;
		}
		for (int i=0;i<pdf.length;i++) {
			pdf[i] = pdf[i]/sum;
		}

		return this;
	}

	public DiscreteProbDensity smooth() {
		int peak = 0;
		for (int i=0;i<pdf.length;i++) {
			if (pdf[i] > pdf[peak]) peak = i;
		}

		double sum = pdf[peak];
		boolean zeroset = false;
		int idx = peak;

		while (idx<pdf.length-1) {
			idx++;
			if (zeroset || pdf[idx] < 0) {
				zeroset = true;
				pdf[idx]=Math.abs(pdf[idx]/(idx-peak));
			}
			sum+=pdf[idx];
		}
		idx = peak;
		zeroset = false;
		while (idx>0) {
			idx--;
			if (zeroset || pdf[idx] < 0) {
				zeroset = true;
				//pdf[idx]=Math.abs(pdf[idx]/(peak-idx))
				pdf[idx]=0;
			}
			sum+=pdf[idx];
		}
		/*
		for (int i=0;i<pdf.length;i++){
			if (pdf[i] < 0) pdf[i] = 0
			sum += pdf[i]
		}
		 */

		if (sum <= 0) {
			log.debug("***** PDF smoothing no sum: set peak at {} pdf {} / sum {} to 1.0",new Object[] { peak,pdf[peak],sum });
			sum = 1.0;
			pdf[peak] = 0.999;
			pdf[peak+1] = 0.001;
		}
		for (int i=0;i<pdf.length;i++){
			pdf[i] = pdf[i]/sum;
		}
		return this;
	}

	public DiscreteProbDensity shiftByValue(double vshift) {
		if (vshift > 0) return rshiftByValue(vshift);
		else return lshiftByValue(-vshift);
	}
	public DiscreteProbDensity rshiftByValue(double vshift) {
		DiscreteProbDensity result = duplicate();
		int shift = (int)Math.round(vshift/interval);
		for (int i=result.pdf.length-1;i >= shift;i--){
			result.pdf[i] = result.pdf[i-shift];
		}
		for (int i=0;i<shift;i++){
			result.pdf[i] = 0;
		}
		return result;
	}
	public DiscreteProbDensity lshiftByValue(double vshift) {
		DiscreteProbDensity result = duplicate();
		int shift = (int)Math.round(vshift/interval);
		//def sum = 0
		for (int i=0;i<result.pdf.length;i++) {
			if (i+shift >= result.pdf.length) result.pdf[i] = 0;
			else {
				//if (i<shift) sum+=result.pdf[i]
				result.pdf[i] = result.pdf[i+shift];
			}
		}
		//result.pdf[0] += sum
		result.doNormalize();
		return result;
	}

	// Shift by index here
	public DiscreteProbDensity rshift(int shift) {
		DiscreteProbDensity result = duplicate();
		for (int i=result.pdf.length-1;i >= shift;i--){
			result.pdf[i] = result.pdf[i-shift];
		}
		for (int i=0;i<shift;i++){
			result.pdf[i] = 0;
		}
		return result;
	}

	public DiscreteProbDensity duplicate() {
		/*
		DiscreteProbDensity result = new DiscreteProbDensity(numSlots,min,max,offset)
		for (int i=0;i<pdf.length;i++){
			result.pdf[i] = pdf[i]
		}
		 */
		DiscreteProbDensity result = new DiscreteProbDensity(this);
		return result;
	}

	// Given another pdf and its conditional prob, calculate the merged pdf
	public DiscreteProbDensity distribute(double condProb, DiscreteProbDensity densityPair) {
		DiscreteProbDensity result = duplicate();
		double sum = 0;
		for (int i=0; i<result.pdf.length;i++){
			result.pdf[i] = ((1-condProb)*result.pdf[i]) + (condProb*densityPair.pdf[i]);
			sum += result.pdf[i];
		}
		if (sum <= 0) sum = 1;
		for (int i = 0; i<result.pdf.length; i++) {
			result.pdf[i] = result.pdf[i]/sum;
		}
		return result;
	}

	// Given another pdf and its conditional prob, calculate the merged pdf
	public static DiscreteProbDensity distribute(double[] condProb, DiscreteProbDensity[] pdfs) {
		DiscreteProbDensity result = new DiscreteProbDensity(pdfs[0]);
		double sum = 0;
		for (int i=0; i<result.pdf.length;i++){
			result.pdf[i] = 0;
			for (int idx = 0; idx<condProb.length;idx++) {
				result.pdf[i] += condProb[idx]*pdfs[idx].pdf[i];				
			}
			sum += result.pdf[i];
		}
		if (sum <= 0) sum = 1;
		for (int i = 0; i<result.pdf.length; i++) {
			result.pdf[i] = result.pdf[i]/sum;
		}
		return result;
	}
	
	// Given another pdf, calculate the merged pdf, assuming uniform distribution
	public DiscreteProbDensity distribute(DiscreteProbDensity densityPair) {
		DiscreteProbDensity result = duplicate();
		double sum = 0;
		for (int i=0; i<result.pdf.length;i++){
			result.pdf[i] += densityPair.pdf[i];
			sum += result.pdf[i];
		}
		if (sum <= 0) sum = 1;
		for (int i=0;i<result.pdf.length;i++) {
			result.pdf[i] = result.pdf[i]/sum;
		}
		return result;
	}

	// Given an input pdf and its probability, calculate the pdf of the current one subtracted by the input
	public DiscreteProbDensity remainDistribute(double prob,DiscreteProbDensity densityPair) {
		DiscreteProbDensity result = duplicate();
		double sum = 0;
		for (int i=0; i<result.pdf.length;i++){
			result.pdf[i] -= densityPair.pdf[i]*prob;
			if (result.pdf[i] < 0) result.pdf[i] = 0;
			sum += result.pdf[i];
		}
		if (sum <= 0) sum = 1;
		for (int i=0; i < result.pdf.length; i++) {
			result.pdf[i] = result.pdf[i]/sum;
		}
		return result;
	}

	// Given a pdf and its conditional probability, extract the original pdf
	public DiscreteProbDensity extract(double condProb, DiscreteProbDensity densityPair) {
		DiscreteProbDensity result = duplicate();
		double sum = 0;
		for (int i=0; i<result.pdf.length;i++){
			result.pdf[i] -= condProb*densityPair.pdf[i]/(1-condProb);
			// make sure the value remain positive
			if (result.pdf[i] < 0) {
				result.pdf[i] = 0;
			}
			sum += result.pdf[i];
		}
		if (sum <= 0) sum = 1;
		for (int i = 0; i < result.pdf.length; i++) {
			result.pdf[i] = result.pdf[i]/sum;
		}
		return result;
	}
	
	static boolean doIntermediatePlot = false;
	public static DiscreteProbDensity lucyDeconv(DiscreteProbDensity blur, DiscreteProbDensity psf) {
		DiscreteProbDensity result = new DiscreteProbDensity(blur);
		// get uniform initial
		for (int i=0;i<result.pdf.length;i++) {
			result.pdf[i] = 1.0/result.pdf.length;
			//result.pdf[i] = psf.pdf[i];
		}
		result = result.normalize();
		
		StringBuilder builder = new StringBuilder();
		if (doIntermediatePlot) {
			builder.append("figure;hold on;");
		}

		for (int iter=0;iter<10;iter++) {
			// For each iteration;
			// f_i+1(t) = { [blur(t)/(f_i(t)*psf(t))] * psf(-t) } x f_i(t)
			// * is convolution, x = pointwise multiplication, *psf(-t) = cross-correlation
			
			// find the inner deconvolution D; blur(t)/(f_i(t)*psf(t))
			double[] div = new double[result.pdf.length];
			for (int x=0;x<result.pdf.length;x++) {
				double sum = 0;
				for (int k=0;k<result.pdf.length;k++) {
					if (x-k < 0) {
						break;
					}
					sum += psf.pdf[k]*result.pdf[x-k];
				}
				if (sum == 0) {
					sum = 1.0;
				}
				div[x] = blur.pdf[x] / sum;
			}
			// find the outer cross correlation xcorr(psf,D) and update the iteration
			for (int x=0;x<result.pdf.length;x++) {
				double sum = 0;
				for (int k=0;k<result.pdf.length;k++) {
					if (x+k >= result.pdf.length) {
						break;
					}
					sum += div[x+k]*psf.pdf[k];
				}
				result.pdf[x] = result.pdf[x]*sum;
			}
			
			if (doIntermediatePlot) {
				builder.append("iter_"+iter+"=");
				result.printBuilder(builder);
				builder.append("\nplot(iter_"+iter+");\n");
			}
		}
		
		// zero-fill the deconvolution, so we always have a normalized result
		/*
		double sum = 0;
		for (double k:result.pdf) {
			sum+=k;
		}
		if (sum < 1.0) {
			result.pdf[0] += 1.0-sum;
		}
		*/
		return result;//.normalize();
	}
	
	public DiscreteProbDensity normalDist(double mean, double std) {
		DiscreteProbDensity result = new DiscreteProbDensity();
		double x;
		for (int i=0;i<result.pdf.length;i++) {
			x = i*interval;
			result.pdf[i] = Math.exp(-(x-mean)*(x-mean)/(2*std*std))/(std*Math.sqrt(2.0*Math.PI));
		}
		return result;
	}
	
	public DiscreteProbDensity getUpperDistribution(double lowerbound) {
		DiscreteProbDensity result = new DiscreteProbDensity(this);
		int start = percentileIndex(lowerbound * 100);
		for (int i = 0; i<start;i++) {
			result.pdf[i] = 0;
		}
		double sum = 0.0;
		for (int i = start; i < result.pdf.length; i++) {
			sum += result.pdf[i];
		}
		for (int i = start; i < result.pdf.length; i++) {
			result.pdf[i] = result.pdf[i] / sum;
		}
		return result;
	}
	
	public DiscreteProbDensity getLowerDistribution(double upperbound) {
		DiscreteProbDensity result = new DiscreteProbDensity(this);
		int start = percentileIndex(upperbound * 100);
		for (int i = start; i<result.pdf.length;i++) {
			result.pdf[i] = 0;
		}
		double sum = 0.0;
		for (int i = 0; i < start; i++) {
			sum += result.pdf[i];
		}
		for (int i = 0; i < start; i++) {
			result.pdf[i] = result.pdf[i] / sum;
		}
		return result;
	}
	
	public static DiscreteProbDensity piecewiseDivide(DiscreteProbDensity a, DiscreteProbDensity b) {
		DiscreteProbDensity result = new DiscreteProbDensity(a);
		for (int i=0;i<result.pdf.length;i++) {
			if (b.pdf[i] == 0) {
				result.pdf[i] = a.pdf[i];
			} else {
				result.pdf[i] = a.pdf[i] / b.pdf[i];
			}
		}
		return result;
	}

	public static DiscreteProbDensity piecewiseMultiply(DiscreteProbDensity a, DiscreteProbDensity b) {
		DiscreteProbDensity result = new DiscreteProbDensity(a);
		for (int i=0;i<result.pdf.length;i++) {
			result.pdf[i] = a.pdf[i] * b.pdf[i];
		}
		return result;
	}
	
	public double[] generateRaw() {
		int sampleCount = 1000;
		List<Double> samples = new LinkedList<Double>();
		double initValue = interval/2;
		for (int i=0;i<pdf.length;i++) {
			int limit = (int)Math.round(pdf[i]*sampleCount);
			if (limit > 0) {
				double incremental = interval/limit;
				for (int k=0;k<limit;k++) {
					samples.add(initValue+k*incremental);
				}
			}
			initValue += interval;
		}
		log.debug("**Generate {} samples",samples.size());
		while (samples.size() < sampleCount) {
			samples.add(initValue);
		}
		while (samples.size() > sampleCount) {
			samples.remove(0);
		}

		double[] out = new double[sampleCount];
		Iterator<Double> iter = samples.iterator();
		for (int i=0;i<sampleCount;i++) {
			out[i] = iter.next();
		}
		return out;
	}

	public double[] getQuantile(double[] qArray) {
		int qIndex = 0;
		double[] result = new double[qArray.length];
		double sum = 0;
		for (int i=0;i<numSlots;i++) {
			sum = sum+pdf[i];
			while (sum >= qArray[qIndex]) {
				result[qIndex] = interval*(i + (qArray[qIndex] - (sum-pdf[i])) / pdf[i]) - offset;
				qIndex++;
				if (qIndex == qArray.length) {
					return result;
				}
			}
		}
		return result;
	}

	public void setPdf(double[] pdf) {
		this.pdf = pdf;
	}
	
	public Long getRawCount() {
		return rawCount;
	}

	public DiscreteProbDensity setRawCount(long rawCount) {
		this.rawCount = rawCount;
		return this;
	}

	public double[] getPdf() {
		return pdf;
	}
	
	public double[] getRaw() {
		return raw;
	}
	
	/**
	 * @return the index of pdf if we take a random sampling from this distribution
	 */
	public int random() {
		double rand = Math.random();
		double sum = 0;
		for (int i = 0; i < pdf.length; i++) {
			sum += pdf[i];
			if (sum >= rand) {
				return i;
			}
		}
		return pdf.length-1;
	}

	public static DiscreteProbDensity expPdf(double lambda) {
		DiscreteProbDensity result = new DiscreteProbDensity();
		for (int i = 0; i < result.getPdf().length; i++) {
			result.getPdf()[i] = lambda*Math.exp(-lambda*i);
		}
		return result;
	}
	
	public static DiscreteProbDensity uniformPdf() {
		DiscreteProbDensity result = new DiscreteProbDensity();
		for (int i = 0; i < result.getPdf().length; i++) {
			result.getPdf()[i] = 1.0/result.getPdf().length;
		}
		return result;
	}
	
	/**
	 * Dirac delta dpf, peak at index, DO NOT USE THIS TO CONVOLVE WITH ANYTHING
	 * @param index
	 * @return
	 */
	public static DiscreteProbDensity deltaPdf(int index) {
		DiscreteProbDensity result = new DiscreteProbDensity();
		result.getPdf()[index] = 1.0;
		return result;
	}	
	
	public static double poisson(double k, double lambda) {
		double result = Math.exp(-lambda);
		for (int i=1;i<=k;i++) {
			result = result * lambda / i;
		}
		return result;
	}	
	
	static double[] guess;
	public static void main(String[] args) {
		DiscreteProbDensity a = new DiscreteProbDensity().normalDist(1000, 100);
		DiscreteProbDensity b = new DiscreteProbDensity().normalDist(1500, 100);
		DiscreteProbDensity c = new DiscreteProbDensity().normalDist(1300, 100);
		DiscreteProbDensity d = new DiscreteProbDensity().normalDist(1800, 100);
		
		DiscreteProbDensity mix1 = DiscreteProbDensity.distribute(new double[] {0.5, 0.5}, new DiscreteProbDensity[] {a,b});
		DiscreteProbDensity mix2 = DiscreteProbDensity.distribute(new double[] {0.5, 0.5}, new DiscreteProbDensity[] {c,d});
		lucyDeconv(mix2, mix1);
		/*
		DiscreteProbDensity dpd = new DiscreteProbDensity(10, 0, 100, 0);
		dpd.add(10);
		dpd.add(20);
		dpd.add(20);
		dpd.add(30);
		System.out.println("---A---");
		dpd.print();

		DiscreteProbDensity b = new DiscreteProbDensity(10, 0, 100, 0);
		b.add(10);
		b.add(10);
		b.add(20);
		System.out.println("---B---");
		b.print();

		System.out.println("---A + B (trunc)--");
		DiscreteProbDensity dpt = dpd.tconv(b);
		dpt.print();

		System.out.println("--- A + B - A (Deconv) ---");
		dpt.deconv(b).print();

		*/
		/*
		int limit = 200;
		DiscreteProbDensity xdpd = new DiscreteProbDensity(1000, 0, 200, 0);
		DiscreteProbDensity ydpd = new DiscreteProbDensity(1000, 0, 200, 0);
		
		NormalDistribution normalx = new NormalDistribution(50, 10);
		NormalDistribution normaly = new NormalDistribution(100, 10);
		
		for (int i=0;i<50000;i++) {
			double norm = normalx.sample();
			if (norm < 0) norm = 0;
			if (norm > limit) norm = limit;
			xdpd.add(norm);

			double ynorm = normaly.sample();
			if (ynorm < 0) ynorm = 0;
			if (ynorm > limit) ynorm = limit;
			ydpd.add(ynorm);
		}
		
		xdpd = xdpd.normalize();
		ydpd = ydpd.normalize();
		final DiscreteProbDensity source = xdpd;
		final double[] result = new double[1000];
		final double[] target = ydpd.pdf;
		final double[] initial = new double[ydpd.pdf.length];
		for (int i=0;i<ydpd.pdf.length;i++) {
			initial[i] = (ydpd.pdf[i]+xdpd.pdf[i])/2;
		}
		
		BOBYQAOptimizer boby = new BOBYQAOptimizer(10000);
		try {
		PointValuePair p = boby.optimize(1000, new MultivariateFunction() {
			@Override
			public double value(double[] point) {
				int slots = 1000;
				
				for (int x=0;x<slots;x++) {
					double sum = 0;
					int stop = (x<slots)?x:slots-1;
					int start = (x<slots)?0:x-slots+1;
					for (int n = start; n<=stop; n++) {
						sum += source.pdf[x-n] * point[n];
					}
					result[x] = sum;
				}
				
				double diff = 0;
				for (int i=0;i<slots;i++) {
					diff += (result[i]-target[i])*(result[i]-target[i]);
				}
				guess = point;
				return diff;
			}
		}, GoalType.MINIMIZE, initial);
		guess = p.getPoint();
		} catch (Exception e) {
			// ignore
		}
		DiscreteProbDensity zdpd = new DiscreteProbDensity(1000, 0, 200, 0);
		zdpd.pdf = guess;
		
		System.out.print("a=");
		xdpd.print();

		System.out.print("b=");
		ydpd.print();
		
		System.out.print("c=");
		zdpd.print();
		
		System.out.println("figure;hold on;plot(a);plot(b);");
		*/
		/*
		DiscreteProbDensity zdpd = xdpd.deconv(ydpd).normalize();

		System.out.println("--X+Z=Y--");
		xdpd.tconv(zdpd).normalize().print();
		System.out.println("--Z=X-Y--");
		zdpd.print();
		*/
		
		
		/*
		DiscreteProbDensity dpdcb = dpd.conv(b);
		System.out.println("---A + B (notrunc)----");
		dpdcb.print();
		 */

		/*
		System.out.println("---A + B - B (trunc) ----");
		dpt.tcov(b).print();

		System.out.println("---A - A (autocorr)----");
		dpd.tcov(dpd).print();
		 */
	}
}
