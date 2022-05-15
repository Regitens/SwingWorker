package com.bazhan;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOError;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

public class Main {

    //команды для загрузки текстового файла и отмены процесса загрузки
    public static void main(String[] args) {
        EventQueue.invokeLater(()->{
            var frame=new SwingWorkerFrame();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setVisible(true);
        });
    }

}

class SwingWorkerFrame extends JFrame
{
    private JFileChooser chooser;
    private JTextArea textArea;
    private JLabel statusLine;
    private JMenuItem openItem;
    private JMenuItem cancelItem;
    private SwingWorker<StringBuilder, ProgressData> textReader;
    public static final int TEXT_ROWS = 20;
    public static final int TEXT_COLUMNS = 60;

    public SwingWorkerFrame()
    {
        chooser = new JFileChooser();
        chooser.setCurrentDirectory(new File("."));

        textArea = new JTextArea(TEXT_ROWS, TEXT_COLUMNS);
        add(new JScrollPane(textArea));

        statusLine = new JLabel(" ");
        add(statusLine, BorderLayout.SOUTH);

        var menuBar = new JMenuBar();
        setJMenuBar(menuBar);

        var menu = new JMenu("File");
        menuBar.add(menu);

        openItem = new JMenuItem("Open");
        menu.add(openItem);
        openItem.addActionListener(event -> {
            // показать диалоговое окно для выбора файлов
            int result = chooser.showOpenDialog(null);

            // если файл выбран, задать его в качестве пиктограммы для метки
            if (result == JFileChooser.APPROVE_OPTION)
            {
                textArea.setText("");
                openItem.setEnabled(false);
                textReader = new TextReader(chooser.getSelectedFile());
                textReader.execute();
                cancelItem.setEnabled(true);
            }
        });

        cancelItem = new JMenuItem("Cancel");
        menu.add(cancelItem);
        cancelItem.setEnabled(false);
        cancelItem.addActionListener(event -> textReader.cancel(true));
        pack();
    }

    //тривиальный внутренний класс
    private class ProgressData{
        //текущий номер строки
        public int number;
        //сама строка
        public String line;
    }

    //для обработки номера и строки из тривиального класса
    private class TextReader extends SwingWorker<StringBuilder, ProgressData> {
        private File file;
        private StringBuilder text = new StringBuilder();

        public TextReader(File file) {
            this.file = file;
        }

        //выполняется в рабочем потоке , не затрагивая компоненты Swing
        public StringBuilder doInBackground() throws IOException, InterruptedException {
            int lineNumber = 0;
            try (var in = new Scanner(new FileInputStream(file), StandardCharsets.UTF_8)) {
                while (in.hasNextLine()) {
                    String line = in.nextLine();
                    lineNumber++;
                    text.append(line).append("\n");
                    var data = new ProgressData();
                    data.number = lineNumber;
                    data.line = line;
                    //Передача номера прочитанной строки и ее содержимого
                    publish(data);
                    Thread.sleep(1); //только для проверки отмены, остановка на милисекунду
                }
            }
            return text;
        }

        //выполняются в потоке диспетчеризации событий
        //игнорируются все номера строк кроме последней, а все прочитанные сцепляются для единого
        //обновления единой текстовой области
        public void process(List<ProgressData> data) {
            if (isCancelled()) return;
            var builder = new StringBuilder();
            statusLine.setText(""+data.get(data.size()-1).number);
            for (ProgressData d:data) builder.append(d.line).append("\n");
            textArea.append(builder.toString());
        }

        //текстовая область обновляется полным текстом а Cancel становится недоступным
        public void done(){
            try{
                StringBuilder result=get();
                textArea.setText(result.toString());
                statusLine.setText("Done");
            }
            catch (InterruptedException ex){}
            catch (CancellationException ex){
                textArea.setText("");
                statusLine.setText("Cancelled");
            }
            catch (ExecutionException ex){
                statusLine.setText(""+ex.getCause());
            }
            cancelItem.setEnabled(false);
            openItem.setEnabled(true);
        }
    }
}
