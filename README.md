# Tenant Management System

Java 17 and MongoDB. TenantApp is the original Swing desktop program. TenantWeb is the browser demonstration for assessment. They use separate databases. The web version shows four collections in TenantClassDemo and creates five fictional examples per collection on startup. The examples can be searched. In the Tenants tab, a visitor can add, edit and delete their own fictional records using the same browser. The supplied example records remain available to the next visitor. Records are linked by unit number and receipt number. The public demo omits contact and national ID fields.

## Try locally on Windows

Open this folder in VS Code. You should see Dockerfile and pom.xml alongside src. Start MongoDB. With JDK 17 or newer and Maven installed, use the PowerShell terminal:

    $env:MONGODB_URI = "mongodb://127.0.0.1:27017"
    mvn compile dependency:copy-dependencies
    java -cp "target/classes;target/dependency/*" TenantWeb

Open http://localhost:8080. Use Ctrl+C to stop the web server. The original Swing application can still run through its main method in VS Code.

## Publish for marking

1. Create a GitHub repository. Upload Dockerfile, pom.xml, README.md and the complete src folder into the repository root. Commit the files. Copy the repository URL.
2. Create a MongoDB Atlas project and a free cluster. Under database access create a dedicated database user with readWrite on TenantClassDemo. Under Connect > Drivers > Java copy the connection string. Replace the password placeholder locally. Keep the real URI out of the repository. A long random password containing only letters and digits avoids URI-encoding issues.
3. On Render create a Web Service linked to the GitHub repository. Set the runtime to Docker, the branch to your uploaded branch, and leave Root Directory empty. Choose a free instance if offered. In Environment add MONGODB_URI with the Atlas connection string. Deploy.
4. Open Connect > Outbound on that service, and add each listed CIDR range to the Atlas project's IP access list. If the first deployment failed waiting for MongoDB, choose Manual Deploy > Deploy latest commit after the access list is active.
5. Open the onrender.com service URL. Verify the four tabs and create, search, edit and delete a fictional tenant. Copy that live URL for the submission form. Never use the Atlas connection string as a submission link.
