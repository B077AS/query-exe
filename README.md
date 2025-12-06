# QueryExe

<div align="center">

**Universal Database Management Tool**

A powerful, modern database editor built with JavaFX that brings professional database management capabilities to your desktop.

[![Java Version](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/)
[![JavaFX](https://img.shields.io/badge/JavaFX-22-blue.svg)](https://openjfx.io/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

[Features](#-features) • [Supported Databases](#️-supported-databases) • [Installation](#-installation) • [Usage](#-usage) • [Building](#️-building)

</div>

---

## ✨ Features

### 🔌 Multi-Database Support
Connect to and manage multiple database systems simultaneously:
- **MySQL** - World's most popular open source database
- **MariaDB** - Enhanced MySQL fork with advanced features
- **PostgreSQL** - Advanced open source relational database
- **SQL Server** - Microsoft's enterprise database platform
- **H2** - Fast, lightweight embedded database
- **SQLite** - Self-contained, serverless database engine

### ⚡ Core Capabilities

**Connection Management**
- Manage multiple database connections simultaneously
- Active concurrent connection monitoring
- Custom JDBC driver configuration

**Query Execution**
- Run multiple queries concurrently
- Advanced SQL editor with syntax highlighting
- Intelligent SQL code suggestions and autocomplete
- Save and organize SQL queries for reuse

**Schema Management**
- Browse and navigate databases, schemas, tables, and columns
- View detailed table and column properties
- Create, modify, and delete tables
- Create and drop databases/schemas

**Data Manipulation**
- Edit result sets directly in the grid
- Generate DDL scripts automatically
- Create INSERT scripts from data
- Export data in multiple formats:
  - CSV
  - JSON
  - XML

**Developer Experience**
- Modern, intuitive JavaFX interface
- Syntax highlighting for SQL code
- Intelligent code completion
- Fast and responsive UI
- Cross-platform compatibility

---

## 🗄️ Supported Databases

| Database | Pre-installed Driver Version |
|----------|------------------------------|
| MySQL | 9.5.0 |
| MariaDB | 3.5.6 |
| PostgreSQL | 42.7.8 |
| SQL Server | 13.2.1.jre11 |
| H2 | 2.4.240 |
| SQLite | 3.51.1.0 |

*All JDBC drivers can be customized by the user to support different versions or configurations.*

---

## 🚀 Installation

### Prerequisites
- Java 21 or higher
- Maven 3.6+ (for building from source)

### Download
Download the latest release from the [Releases](../../releases) page.

### Run
```bash
java -jar queryexe-1.0.0.jar
```

---

## 💻 Usage

### Creating a Connection
1. Click the **New Connection** button
2. Select your database type
3. Enter connection details (host, port, database, credentials)
4. Test the connection
5. Save and connect

### Running Queries
1. Open a new query tab
2. Write your SQL query with syntax highlighting and autocomplete
3. Execute with `Ctrl+Enter` or click the Run button
4. View results in the data grid
5. Edit cells directly or export results

### Managing Tables
- **Right-click** on any table to access table operations
- **Create Table**: Define columns, types, and constraints
- **Alter Table**: Modify structure and properties
- **Drop Table**: Remove tables with confirmation
- **View Properties**: Inspect table metadata and statistics

### Exporting Data
1. Execute a query to generate a result set
2. Click **Export**
3. Choose format (CSV, JSON, or XML)
4. Select destination and export

---

## 🛠️ Building from Source

### Clone the Repository
```bash
git clone https://github.com/B077AS/query-exe
cd query-exe
```

### Build with Maven
```bash
# Build with platform-specific JavaFX dependencies
mvn clean package -Ppackage
```

### Run from Source
```bash
mvn javafx:run
```

The compiled JAR will be available in the `target/` directory.

---

## 🏗️ Technology Stack

- **Java 21** - Modern LTS Java version
- **JavaFX 22** - Rich desktop UI framework
- **AtlantaFX** - Modern JavaFX theme
- **RichTextFX** - Advanced text editing component
- **JDBC Drivers** - Native database connectivity
- **Maven** - Build and dependency management
- **Lombok** - Boilerplate reduction

---

## 🎨 Screenshots

### Overall Design
![Overall Design](https://github.com/user-attachments/assets/d5023cd9-6c53-406b-a9a8-8925b4841dfe)

### Table Options
![Table Options](https://github.com/user-attachments/assets/c1cb5c79-cda0-4a85-b86b-59ba081c9df0)

### Connection Creation
![Connection Creation](https://github.com/user-attachments/assets/3ec617ef-c397-4b1c-8a8c-abf1e2581b81)

### Editor Features
![Editor Features](https://github.com/user-attachments/assets/5f03350a-4edd-4d49-9d27-ec8a302f4f33)

---

## 📋 Roadmap

- [ ] ER diagram generation
- [ ] Theme selection
- [ ] Query performance analysis
- [ ] Database backup and restore

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the MIT License

---

## 🙏 Acknowledgments

- Built with [JavaFX](https://openjfx.io/)
- UI powered by [AtlantaFX](https://github.com/mkpaz/atlantafx)
- Icons from [Ikonli](https://kordamp.org/ikonli/)
- SQL editing with [RichTextFX](https://github.com/FXMisc/RichTextFX)

---

<div align="center">

**Made with Java**

If you find QueryExe useful, consider giving it a ⭐!

</div>