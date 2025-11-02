# Query-exe

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
| MySQL | 8.0.33 |
| MariaDB | 3.1.4 |
| PostgreSQL | 42.6.0 |
| SQL Server | 12.2.0 |
| H2 | 2.2.220 |
| SQLite | 3.42.0.0 |

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
cd queryexe
```

### Build with Maven
```bash
# Standard build
mvn clean package

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
![Overall Design](https://private-user-images.githubusercontent.com/118758182/508750642-6313669c-2a42-4f86-9163-467e0871edae.png?jwt=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJnaXRodWIuY29tIiwiYXVkIjoicmF3LmdpdGh1YnVzZXJjb250ZW50LmNvbSIsImtleSI6ImtleTUiLCJleHAiOjE3NjIxMTc5NTIsIm5iZiI6MTc2MjExNzY1MiwicGF0aCI6Ii8xMTg3NTgxODIvNTA4NzUwNjQyLTYzMTM2NjljLTJhNDItNGY4Ni05MTYzLTQ2N2UwODcxZWRhZS5wbmc_WC1BbXotQWxnb3JpdGhtPUFXUzQtSE1BQy1TSEEyNTYmWC1BbXotQ3JlZGVudGlhbD1BS0lBVkNPRFlMU0E1M1BRSzRaQSUyRjIwMjUxMTAyJTJGdXMtZWFzdC0xJTJGczMlMkZhd3M0X3JlcXVlc3QmWC1BbXotRGF0ZT0yMDI1MTEwMlQyMTA3MzJaJlgtQW16LUV4cGlyZXM9MzAwJlgtQW16LVNpZ25hdHVyZT04ZGJhYTZhOTFkNjczNTdjMTE2ZmQ2NjgwOWU1OWFiNDlhODI3NTVjMjcyNzc1ZDBmMWYwZjk1ZjU2Zjc4ZGFlJlgtQW16LVNpZ25lZEhlYWRlcnM9aG9zdCJ9.1jZ6PMe2sFYFlpuiCe8AVFAOviu7MxVDYlij7TlyXJ0)

### Table Options
![Table Options](https://private-user-images.githubusercontent.com/118758182/508750641-db300da5-ad61-4cf6-8adc-511581d766a7.png?jwt=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJnaXRodWIuY29tIiwiYXVkIjoicmF3LmdpdGh1YnVzZXJjb250ZW50LmNvbSIsImtleSI6ImtleTUiLCJleHAiOjE3NjIxMTc5NTIsIm5iZiI6MTc2MjExNzY1MiwicGF0aCI6Ii8xMTg3NTgxODIvNTA4NzUwNjQxLWRiMzAwZGE1LWFkNjEtNGNmNi04YWRjLTUxMTU4MWQ3NjZhNy5wbmc_WC1BbXotQWxnb3JpdGhtPUFXUzQtSE1BQy1TSEEyNTYmWC1BbXotQ3JlZGVudGlhbD1BS0lBVkNPRFlMU0E1M1BRSzRaQSUyRjIwMjUxMTAyJTJGdXMtZWFzdC0xJTJGczMlMkZhd3M0X3JlcXVlc3QmWC1BbXotRGF0ZT0yMDI1MTEwMlQyMTA3MzJaJlgtQW16LUV4cGlyZXM9MzAwJlgtQW16LVNpZ25hdHVyZT1lY2I5YTVlOThkNzMxYzI5NDc3NDIyYmY4ZmZhZDY0MjU0M2NiNDEzODM5OWJkNzI0NTc1YzIyNjhhM2VlMmRmJlgtQW16LVNpZ25lZEhlYWRlcnM9aG9zdCJ9.BglXh7cW2Ls9XT9m-OGLuqlIJvIcDDULg-hOEUs-L-o)

### Connection Creation
![Connection Creation](https://private-user-images.githubusercontent.com/118758182/508750640-62c6151c-d6c8-4fa2-9f48-9c0ccee57f87.png?jwt=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJnaXRodWIuY29tIiwiYXVkIjoicmF3LmdpdGh1YnVzZXJjb250ZW50LmNvbSIsImtleSI6ImtleTUiLCJleHAiOjE3NjIxMTc5NTIsIm5iZiI6MTc2MjExNzY1MiwicGF0aCI6Ii8xMTg3NTgxODIvNTA4NzUwNjQwLTYyYzYxNTFjLWQ2YzgtNGZhMi05ZjQ4LTljMGNjZWU1N2Y4Ny5wbmc_WC1BbXotQWxnb3JpdGhtPUFXUzQtSE1BQy1TSEEyNTYmWC1BbXotQ3JlZGVudGlhbD1BS0lBVkNPRFlMU0E1M1BRSzRaQSUyRjIwMjUxMTAyJTJGdXMtZWFzdC0xJTJGczMlMkZhd3M0X3JlcXVlc3QmWC1BbXotRGF0ZT0yMDI1MTEwMlQyMTA3MzJaJlgtQW16LUV4cGlyZXM9MzAwJlgtQW16LVNpZ25hdHVyZT00NWYzMWYwOWYxOThiYjFiN2UwOGQ1NzZlYWQ4MTMxZGNlYjkyMDA1OTE4M2EwNjJlOThiMGYzYjM2ODAwZTYwJlgtQW16LVNpZ25lZEhlYWRlcnM9aG9zdCJ9.vMYx98-fOsXVZVE9rmiiI5vFqgey2BmrYq5ugNuiKw0)

### Editor Features
![Editor Features](https://private-user-images.githubusercontent.com/118758182/508750643-f893c379-c2f2-4467-9431-f917159017dd.png?jwt=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJnaXRodWIuY29tIiwiYXVkIjoicmF3LmdpdGh1YnVzZXJjb250ZW50LmNvbSIsImtleSI6ImtleTUiLCJleHAiOjE3NjIxMTc5NTIsIm5iZiI6MTc2MjExNzY1MiwicGF0aCI6Ii8xMTg3NTgxODIvNTA4NzUwNjQzLWY4OTNjMzc5LWMyZjItNDQ2Ny05NDMxLWY5MTcxNTkwMTdkZC5wbmc_WC1BbXotQWxnb3JpdGhtPUFXUzQtSE1BQy1TSEEyNTYmWC1BbXotQ3JlZGVudGlhbD1BS0lBVkNPRFlMU0E1M1BRSzRaQSUyRjIwMjUxMTAyJTJGdXMtZWFzdC0xJTJGczMlMkZhd3M0X3JlcXVlc3QmWC1BbXotRGF0ZT0yMDI1MTEwMlQyMTA3MzJaJlgtQW16LUV4cGlyZXM9MzAwJlgtQW16LVNpZ25hdHVyZT00M2Q4OWU3YThhMTM0MzU1ZTJlNjJjODY5MWY5ZjMyNTBkMzc5YmZiYTUwZGYzYTdhNWQwNTI3M2Y1MjY1MDY3JlgtQW16LVNpZ25lZEhlYWRlcnM9aG9zdCJ9.UI3UDoGJkC54LechvVKm6dxyePzUFGN6aSEpQdODpU0)

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

If you find Query-exe useful, consider giving it a ⭐!

</div>