package com.mycompany.project00;
public class Complex
{
  private double real, imag;    // double-Variablen fuer Realteil, Imaginaerteil

  public Complex(){
    real=0.0;
    imag=0.0;
  }
  public Complex(double real, double imag) // Initialisierung mittels Real- und Imaginaerteil
  {
    this.real = real;
    this.imag = imag;
  }

  public static Complex add(Complex comp1, Complex comp2)   // Addition im Complexen
  {
    return new Complex(comp1.getReal() + comp2.getReal(), comp1.getImag() + comp2.getImag());
  }
  public static Complex mul(Complex comp1, Complex comp2)   // Multiplikation im Complexen
  {
    return new Complex(comp1.getReal() * comp2.getReal()-comp1.getImag()*comp2.getImag(),
                       comp1.getReal()*comp2.getImag() + comp1.getImag()*comp2.getReal());
  }

  public static Complex conj(Complex comp)
  {
    return new Complex(comp.getReal(), -comp.getImag());
  }

  public double getImag()
  {
    return imag;
  }
  
  public void setImag(double imag)
  {
    this.imag = imag;
  }
  
  public double getReal()
  {
    return real;
  }
  
  public void setReal(double real)
  {
    this.real = real;
  }
  
  public double getAbs(){
    return Math.sqrt( real*real + imag*imag );
  }


}
