package com.mycompany.project00;
import java.io.*;
import java.util.*;

public class Rand implements Serializable
{
  private int N;       //Anzahl der Randpunkte

  private LinkedList RandPunkte;


  public Rand(int N, LinkedList RandPunkte)
  {
    this.N = N;
    this.RandPunkte = RandPunkte;
  }

  public void setN(int N)
  {
    this.N = N;
  }

  public void setRandPunkte(LinkedList RandPunkte)
  {
    this.RandPunkte = RandPunkte;
  }

  public int getN()
  {
    return N;
  }

  public LinkedList getRandPunkte()
  {
    return RandPunkte;
  }


}
