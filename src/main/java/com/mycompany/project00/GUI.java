package com.mycompany.project00;

import javax.swing.*;
import java.awt.event.*;
import java.awt.*;

public class GUI extends JFrame{

    private Container con;
    private SimpleMenuBar sb;
    private JButton start;
    private JButton stopper;
    private JButton fullList;
    private JButton openImage;

    public JLabel input;
    public JLabel output,output1,output2,output3,output4;
    public ImageProcessing img_pro;
    public ImageIcon ic;
    public ImageIcon ic_out;

    
    public ImageIcon ic1_out;

    public ImageIcon ic2_out;


    public ImageIcon ic3_out;


    public ImageIcon ic4_out;


    public GUI(ImageProcessing i_pro){

    img_pro=i_pro;

    sb=new SimpleMenuBar(this);
    setJMenuBar(sb);

    con=getContentPane();
    con.setLayout(new FlowLayout(FlowLayout.LEFT));

    start= new JButton ("Start");
 
 stopper= new JButton ("Stopper");

fullList= new JButton ("Full List");

    input= new JLabel ("Input Image");
    output= new JLabel ("Output Image");
 output1= new JLabel ("Output1 Image");
 output2= new JLabel ("Output2 Image");
 output3= new JLabel ("Output3 Image");
 output4= new JLabel ("Output4 Image");

    start.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
        new Thread(() -> img_pro.autoRunAll()).start();
    }
});
 
   stopper.addActionListener(e -> img_pro.stopper());
        fullList.addActionListener(e -> img_pro.fullList());

        //  Disable manual clicking for red seed points
        output2.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // disabled - automatic mode
            }
        });
     
     /* output2.addMouseListener(new MouseAdapter(){
          @Override
          public void mouseClicked(java.awt.event.MouseEvent e){
              return;
          }
      });*/
  
  
  con.add(start);
  con.add(stopper);
  con.add(fullList);
  con.add(input);
  con.add(output);
  con.add(output1);
  con.add(output2);
  con.add(output3);
  con.add(output4);
  
setVisible(true);
pack();
setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }
}
