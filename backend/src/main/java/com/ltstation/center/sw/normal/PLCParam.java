package com.ltstation.center.sw.normal;

import java.math.BigDecimal;

/** NormalModel.PLCParam : mesures et défauts de l'automate (station GNC), envoyés tels quels à la page PLC. */
public class PLCParam {

    public int Statue;
    public BigDecimal A_PistonCurrent = BigDecimal.ZERO;
    public BigDecimal B_PistonCurrent = BigDecimal.ZERO;
    public BigDecimal InTake_Pressure = BigDecimal.ZERO;
    public BigDecimal Low_Pressure = BigDecimal.ZERO;
    public BigDecimal Instrument_Air_Pressure = BigDecimal.ZERO;
    public BigDecimal Medium_Pressure = BigDecimal.ZERO;
    public BigDecimal Gas_Concentration_Inside_the_Sled_First = BigDecimal.ZERO;
    public BigDecimal Hydraulic_Oil_Temperature = BigDecimal.ZERO;
    public BigDecimal High_Pressure = BigDecimal.ZERO;
    public BigDecimal Protection_Pressure = BigDecimal.ZERO;
    public BigDecimal Water_Flow_Temperature = BigDecimal.ZERO;
    public BigDecimal Gas_Concentration_Second = BigDecimal.ZERO;
    public BigDecimal Gas_Concentration_Third = BigDecimal.ZERO;
    public int A_Bourn_Out;
    public int A_Motor_Fault;
    public int B_Bourn_Out;
    public int B_Motor_Fault;
    public int InTake_Pressure_Bourn_Out;
    public int Medium_Pressure_Bourn_Out;
    public int High_Pressure_Bourn_Out;
    public int Protection_Pressure_Bourn_Out;
    public int Medium_Pressure_Lower_No_Gas_Value;
    public int Combustible_Gas_Concentration_Bourn_Out;
    public int Hydraulic_Oil_Temperature_Bourn_Out;
    public int Inlet_Pressure_Lower_Airless_Value;
    public int Protect_Pressure_Above_Set_Value;
    public int Hydraulic_Oil_Shortage;
    public int Hydraulic_Oil_Temperature_Higher_Set;
    public int Circulating_Water_Temperature_Higher_Set;
    public int Low_Instrument_Air_Pressure;
    public int Combustible_Gas_Concentration_Exceeding;
    public int Medium_Pressure_Pneumatic_Not_Fully_Opened;
    public int Circulating_Pneumatic_Not_Fully_Opened;
    public int Hydraulic_Oil_Temperature_Above_Alarm;
    public int Inlet_Pressure_Lower_Inlet_Alarm;
    public int First_Cooling_Fan_Malfunction;
    public int Second_Cooling_Fan_Malfunction;
    public int Instrument_Wind_Sensor_Disconnected;
    public int[] MW28Data;
    public int[] MB5Data;
    public int[] MB10Data;
    public int[] Error;
}
