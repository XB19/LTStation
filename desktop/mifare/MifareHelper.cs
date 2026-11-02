// Passerelle 32 bits vers mwrf32.dll : le serveur tourne sur un Java 64 bits, qui ne peut pas charger cette DLL.
// Une commande par ligne sur l'entrée standard, une réponse par ligne sur la sortie standard.
//   init port baud | select h | auth h mode secteur cléhex | read h bloc | write h bloc hex | exit h
using System;
using System.Runtime.InteropServices;
using System.Text;

static class MifareHelper
{
    [DllImport("mwrf32.dll", CallingConvention = CallingConvention.StdCall)]
    static extern int rf_init(short port, int baud);

    [DllImport("mwrf32.dll", CallingConvention = CallingConvention.StdCall)]
    static extern short rf_request(int icdev, byte mode, out short tagtype);

    [DllImport("mwrf32.dll", CallingConvention = CallingConvention.StdCall)]
    static extern short rf_anticoll(int icdev, byte bcnt, out uint snr);

    [DllImport("mwrf32.dll", CallingConvention = CallingConvention.StdCall)]
    static extern short rf_select(int icdev, uint snr, out byte size);

    [DllImport("mwrf32.dll", CallingConvention = CallingConvention.StdCall)]
    static extern short rf_authentication_key(int icdev, byte mode, byte secnr, byte[] keybuff);

    [DllImport("mwrf32.dll", CallingConvention = CallingConvention.StdCall)]
    static extern short rf_read_hex(int icdev, byte blocknr, byte[] databuff);

    [DllImport("mwrf32.dll", CallingConvention = CallingConvention.StdCall)]
    static extern short rf_write_hex(int icdev, int blocknr, byte[] databuff);

    [DllImport("mwrf32.dll", CallingConvention = CallingConvention.StdCall)]
    static extern short rf_exit(int icdev);

    static byte[] Bytes(string hex)
    {
        byte[] b = new byte[hex.Length / 2];
        for (int i = 0; i < b.Length; i++)
        {
            b[i] = Convert.ToByte(hex.Substring(i * 2, 2), 16);
        }
        return b;
    }

    static string Run(string[] f)
    {
        int h = f.Length > 1 ? int.Parse(f[1]) : 0;
        switch (f[0])
        {
            case "ping":
                return "ok";
            case "init":
                return rf_init(short.Parse(f[1]), int.Parse(f[2])).ToString();
            case "select":
                short tag;
                uint snr;
                byte size;
                rf_request(h, 1, out tag);
                rf_anticoll(h, 0, out snr);
                rf_select(h, snr, out size);
                return "0";
            case "auth":
                return rf_authentication_key(h, byte.Parse(f[2]), byte.Parse(f[3]), Bytes(f[4])).ToString();
            case "read":
                byte[] buf = new byte[32];
                rf_read_hex(h, byte.Parse(f[2]), buf);
                return Encoding.ASCII.GetString(buf).Replace('\0', '0');
            case "write":
                return rf_write_hex(h, int.Parse(f[2]), Encoding.ASCII.GetBytes(f[3])).ToString();
            case "exit":
                return rf_exit(h).ToString();
            default:
                return "err commande";
        }
    }

    static void Main()
    {
        string line;
        while ((line = Console.ReadLine()) != null)
        {
            string answer;
            try
            {
                answer = Run(line.Trim().Split(' '));
            }
            catch (Exception ex)
            {
                answer = "err " + ex.GetType().Name;
            }
            Console.WriteLine(answer);
            Console.Out.Flush();
        }
    }
}
