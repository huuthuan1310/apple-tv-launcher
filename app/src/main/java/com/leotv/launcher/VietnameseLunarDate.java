package com.leotv.launcher;

/** Vietnamese lunisolar dates at UTC+7, using new moons and solar terms (Meeus).
 * Calendar rules: https://www.informatik.uni-leipzig.de/~duc/amlich/calrules.html
 */
final class VietnameseLunarDate {
    private static final double RAD = Math.PI / 180;
    private static int floor(double value) { return (int)Math.floor(value); }
    private static int jd(int day,int month,int year) {
        int a=(14-month)/12, y=year+4800-a, m=month+12*a-3;
        return day+(153*m+2)/5+365*y+y/4-y/100+y/400-32045;
    }
    private static int moon(int k) {
        double t=k/1236.85, t2=t*t, t3=t2*t;
        double base=2415020.75933+29.53058868*k+.0001178*t2-.000000155*t3
                +.00033*Math.sin((166.56+132.87*t-.009173*t2)*RAD);
        double m=(359.2242+29.10535608*k-.0000333*t2-.00000347*t3)*RAD;
        double p=(306.0253+385.81691806*k+.0107306*t2+.00001236*t3)*RAD;
        double f=(21.2964+390.67050646*k-.0016528*t2-.00000239*t3)*RAD;
        double correction=(.1734-.000393*t)*Math.sin(m)+.0021*Math.sin(2*m)
                -.4068*Math.sin(p)+.0161*Math.sin(2*p)-.0004*Math.sin(3*p)
                +.0104*Math.sin(2*f)-.0051*Math.sin(m+p)-.0074*Math.sin(m-p)
                +.0004*Math.sin(2*f+m)-.0004*Math.sin(2*f-m)-.0006*Math.sin(2*f+p)
                +.0010*Math.sin(2*f-p)+.0005*Math.sin(2*p+m);
        double dt=t < -11 ? .001+.000839*t+.0002261*t2-.00000845*t3-.000000081*t*t3
                : -.000278+.000265*t+.000262*t2;
        return floor(base+correction-dt+.5+7.0/24);
    }
    private static int solarTerm(int day) {
        double t=(day-2451545.5-7.0/24)/36525, t2=t*t;
        double m=(357.52910+35999.05030*t-.0001559*t2-.00000048*t*t2)*RAD;
        double longitude=280.46645+36000.76983*t+.0003032*t2
                +(1.914600-.004817*t-.000014*t2)*Math.sin(m)
                +(.019993-.000101*t)*Math.sin(2*m)+.000290*Math.sin(3*m);
        double radians=longitude*RAD;
        radians-=2*Math.PI*Math.floor(radians/(2*Math.PI));
        return floor(radians/Math.PI*6);
    }
    private static int month11(int year) {
        int k=floor((jd(31,12,year)-2415021)/29.530588853);
        int day=moon(k);
        return solarTerm(day)>=9 ? moon(k-1) : day;
    }
    private static int leapOffset(int start) {
        int k=floor((start-2415021.076998695)/29.530588853+.5);
        int i=1, last, arc=solarTerm(moon(k+i));
        do { last=arc; i++; arc=solarTerm(moon(k+i)); } while(arc!=last && i<14);
        return i-1;
    }
    /** Returns day, month, year and leap-month flag. */
    static int[] fromSolar(int day,int month,int year) {
        int number=jd(day,month,year), k=floor((number-2415021.076998695)/29.530588853);
        int start=moon(k+1);
        if(start>number) start=moon(k);
        int a=month11(year), b=a, lunarYear;
        if(a>=start) { lunarYear=year; a=month11(year-1); }
        else { lunarYear=year+1; b=month11(year+1); }
        int lunarDay=number-start+1, diff=floor((start-a)/29.0), leap=0;
        int lunarMonth=diff+11;
        if(b-a>365) {
            int offset=leapOffset(a);
            if(diff>=offset) { lunarMonth=diff+10; if(diff==offset) leap=1; }
        }
        if(lunarMonth>12) lunarMonth-=12;
        if(lunarMonth>=11 && diff<4) lunarYear--;
        return new int[]{lunarDay,lunarMonth,lunarYear,leap};
    }
    static String todayLabel() {
        java.util.Calendar now=java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        int[] date=fromSolar(now.get(java.util.Calendar.DAY_OF_MONTH),now.get(java.util.Calendar.MONTH)+1,now.get(java.util.Calendar.YEAR));
        return "ÂL " + date[0]+"/"+date[1]+(date[3]==1 ? " nhuận" : "");
    }
}
