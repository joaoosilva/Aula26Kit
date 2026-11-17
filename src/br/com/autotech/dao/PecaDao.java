package br.com.autotech.dao;

import br.com.autotech.factory.ConexaoFactory;
import br.com.autotech.model.Peca;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================================
 * KIT DE DESENVOLVIMENTO DE LABORATÓRIO • PECADAO (AUTOTECH)
 * ============================================================================
 * 
 * 🏢 CENÁRIO DE NEGÓCIO:
 * A oficina mecânica AutoTech sofre com oscilações frequentes de rede Wi-Fi em seu galpão.
 * Sua missão como engenheiro de software é implementar a persistência local em SQLite
 * para que os mecânicos consigam cadastrar, consultar e dar baixa nas peças mesmo
 * sem sinal de internet!
 * 
 * Gerencia a persistência local das peças no SQLite.
 * ============================================================================
 */
public class PecaDao {

    /**
     * 🎯 MISSÃO 1: DDL AUTOMÁTICO
     * 
     * Implemente a criação da tabela "pecas" no SQLite se ela ainda não existir.
     * 
     * Estrutura da tabela:
     * - id: INTEGER PRIMARY KEY AUTOINCREMENT
     * - codigo: TEXT UNIQUE NOT NULL
     * - nome: TEXT NOT NULL
     * - preco: REAL NOT NULL
     * - estoque: INTEGER NOT NULL
     * 
     */
    public void criarTabelaSeNaoExistir() {
        String sql = """
            CREATE TABLE IF NOT EXISTS pecas (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                codigo TEXT UNIQUE NOT NULL,
                nome TEXT NOT NULL,
                preco REAL NOT NULL,
                estoque INTEGER NOT NULL
            );
        """;

        try (Connection conn = ConexaoFactory.obterConexao();
             Statement stmt = conn.createStatement()) {

            stmt.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("Não foi possível criar ou verificar a tabela pecas.", e);
        }
    }

    /**
     * 🎯 MISSÃO 2: INSERT COM RESGATE DE CHAVE PRIMÁRIA
     * 
     * Salve a peça no banco usando PreparedStatement parametrizado com interrogações (?).
     * Ao salvar, resgate o ID gerado automaticamente pelo SQLite e defina no objeto:
     * peca.setId(idGerado).
     * 
     * 💡 DICA:
     * 1. conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
     * 2. stmt.setString(1, ...), stmt.setString(2, ...), etc. (lembre-se do índice 1!)
     * 3. stmt.executeUpdate();
     * 4. try (ResultSet chaves = stmt.getGeneratedKeys()) { if (chaves.next()) ... }
     * 
     * @param peca Objeto a ser salvo.
     * @return O mesmo objeto com o ID preenchido.
     */
    public Peca salvar(Peca peca) {
        String sql = "INSERT INTO pecas (codigo, nome, preco, estoque) VALUES (?, ?, ?, ?);";

        if (peca == null) {
            throw new IllegalArgumentException("A peça não pode ser nula.");
        }

        try (Connection conn = ConexaoFactory.obterConexao();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, peca.getCodigo());
            stmt.setString(2, peca.getNome());
            stmt.setDouble(3, peca.getPreco());
            stmt.setInt(4, peca.getEstoque());

            int linhas = stmt.executeUpdate();
            if (linhas > 0) {
                try (ResultSet chaves = stmt.getGeneratedKeys()) {
                    if (chaves.next()) {
                        peca.setId(chaves.getLong(1));
                    } else {
                        throw new SQLException("O SQLite não retornou o ID da peça cadastrada.");
                    }
                }
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Não foi possível salvar a peça.", e);
        }

        return peca;
    }

    /**
     * 🎯 MISSÃO 3: CONSULTA GERAL DE PEÇAS (SELECT)
     * 
     * Consulte todas as peças da tabela ordenadas pelo nome:
     * "SELECT id, codigo, nome, preco, estoque FROM pecas ORDER BY nome;"
     * 
     * 💡 DICA:
     * 1. Use while (rs.next()) para percorrer as linhas.
     * 2. Instancie um new Peca(...) para cada linha lida do ResultSet.
     * 3. Adicione o objeto na lista com lista.add(p).
     * 4. Busque as colunas pelo nome: rs.getString("nome"), rs.getDouble("preco"), etc.
     * 
     * @return Lista de peças encontradas no banco.
     */
    public List<Peca> listarTodas() {
        List<Peca> lista = new ArrayList<>();
        String sql = "SELECT id, codigo, nome, preco, estoque FROM pecas ORDER BY nome;";

        try (Connection conn = ConexaoFactory.obterConexao();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                lista.add(mapearPeca(rs));
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Não foi possível listar as peças.", e);
        }

        return lista;
    }

    /**
     * 🎯 MISSÃO 4: CONSULTA POR CÓDIGO ESPECÍFICO (FILTRAGEM)
     * 
     * Busque uma peça específica através do seu código único (ex: "FIL-101").
     * Query: "SELECT id, codigo, nome, preco, estoque FROM pecas WHERE codigo = ?;"
     * 
     * 💡 DICA:
     * 1. stmt.setString(1, codigo);
     * 2. Como o código é UNIQUE, use if (rs.next()) em vez de while.
     * 3. Se encontrar, retorne o objeto montado; se não encontrar, retorne null.
     * 
     * @param codigo Código alfanumérico da peça.
     * @return Objeto Peca se existir, ou null se não encontrada.
     */
    public Peca buscarPorCodigo(String codigo) {
        String sql = "SELECT id, codigo, nome, preco, estoque FROM pecas WHERE codigo = ?;";

        try (Connection conn = ConexaoFactory.obterConexao();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, codigo);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapearPeca(rs);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Não foi possível buscar a peça pelo código.", e);
        }

        return null;
    }

    /**
     * 🎯 MISSÃO 5: BAIXA DE ESTOQUE NO BALCÃO (UPDATE)
     * 
     * Quando um mecânico retira peças para uma manutenção, dê baixa no estoque:
     * "UPDATE pecas SET estoque = estoque - ? WHERE id = ? AND estoque >= ?;"
     * 
     * 💡 DICA:
     * 1. stmt.setInt(1, quantidadeRetirada);
     * 2. stmt.setLong(2, id);
     * 3. stmt.setInt(3, quantidadeRetirada);
     * 4. int linhas = stmt.executeUpdate();
     * 5. Retorne true se linhas > 0 (sucesso na baixa), ou false se estoque insuficiente.
     * 
     * @param id Identificador da peça.
     * @param quantidadeRetirada Quantidade a subtrair.
     * @return true se atualizou, false se estoque insuficiente ou ID inexistente.
     */
    public boolean darBaixaEstoque(Long id, int quantidadeRetirada) {
        String sql = "UPDATE pecas SET estoque = estoque - ? WHERE id = ? AND estoque >= ?;";

        if (id == null || id <= 0) {
            throw new IllegalArgumentException("O ID da peça deve ser positivo.");
        }
        if (quantidadeRetirada <= 0) {
            throw new IllegalArgumentException("A quantidade retirada deve ser positiva.");
        }

        try (Connection conn = ConexaoFactory.obterConexao();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, quantidadeRetirada);
            stmt.setLong(2, id);
            stmt.setInt(3, quantidadeRetirada);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Não foi possível dar baixa no estoque.", e);
        }
    }

    private Peca mapearPeca(ResultSet rs) throws SQLException {
        return new Peca(
                rs.getLong("id"),
                rs.getString("codigo"),
                rs.getString("nome"),
                rs.getDouble("preco"),
                rs.getInt("estoque")
        );
    }
}
