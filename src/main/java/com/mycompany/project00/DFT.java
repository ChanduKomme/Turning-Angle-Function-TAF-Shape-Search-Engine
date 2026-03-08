package com.mycompany.project00;
public class DFT{

Complex[][] f; //Basisfunktionen

int N=1;

public DFT(int N){

  if(N>0){
  this.N=N;

  f = new Complex[N][N];
  
    calculateBasisFunctions();
  }

}

//komplexe Skalarprodukt im n-Dim-komplexen Raum

public Complex skalar(Complex[] x, Complex[] y){ //Skalarprodukt im N-Dim. komplexen Raum
Complex z=new Complex(0,0);

  for(int i=0; i<N;i++){

    z= Complex.add(z, Complex.mul(x[i], Complex.conj(y[i])));  

  }
  return z;
}


public void calculateBasisFunctions(){

  for(int n=0;n<N;n++){
          for(int k=0;k<N;k++){
          double w=Math.PI* 2.0 * (double)n*(double)k /(double)N;

          Complex z=new Complex(Math.cos(w) , Math.sin(w)); //exp(iw) mit Euler'scher Identitt
          Complex norm=new Complex(1.0 / Math.sqrt(N),0);
          z=Complex.mul(z,norm);   //Normierung
          f[n][k]=z; //Normierte Basisfunktionen
          }
  }
}

public Complex[] transform(Complex[] x){ //DFT

Complex[] c=new Complex[N];
if(x.length==N){

  for(int i=0;i<N; i++){
  c[i]=skalar(x,f[i]);
  }
}
return c;
}


public Complex[] invtrans(Complex[] c){ // Inverse DFT
Complex[] x= new Complex[N];
  for(int k=0;k<N;k++){

    Complex z=new Complex(0.0,0.0);
            for(int n=0;n<N; n++){
            z=Complex.add(z,Complex.mul(c[n],f[n][k]));
            }
  x[k]=z;
  }
  return x;
}



}
