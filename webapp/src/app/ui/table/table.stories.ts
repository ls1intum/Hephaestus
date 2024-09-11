import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { TableComponent } from './table.component';
import { TableBodyComponent } from './table-body.component';
import { TableCaptionComponent } from './table-caption.component';
import { TableCellComponent } from './table-cell.component';
import { TableFooterComponent } from './table-footer.component';
import { TableHeaderComponent } from './table-header.component';
import { TableHeadComponent } from './table-head.component';
import { TableRowComponent } from './table-row.component';

type CustomArgs = {
  invoices: {
    invoice: string;
    paymentStatus: string;
    totalAmount: string;
    paymentMethod: string;
  }[];
};

const meta: Meta<CustomArgs> = {
  title: 'UI/Table',
  component: TableComponent,
  decorators: [
    moduleMetadata({
      imports: [TableBodyComponent, TableCaptionComponent, TableCellComponent, TableFooterComponent, TableHeaderComponent, TableHeadComponent, TableRowComponent]
    })
  ],
  tags: ['autodocs'],
  args: {
    invoices: [
      {
        invoice: 'INV001',
        paymentStatus: 'Paid',
        totalAmount: '$250.00',
        paymentMethod: 'Credit Card'
      },
      {
        invoice: 'INV002',
        paymentStatus: 'Pending',
        totalAmount: '$150.00',
        paymentMethod: 'PayPal'
      },
      {
        invoice: 'INV003',
        paymentStatus: 'Unpaid',
        totalAmount: '$350.00',
        paymentMethod: 'Bank Transfer'
      },
      {
        invoice: 'INV004',
        paymentStatus: 'Paid',
        totalAmount: '$450.00',
        paymentMethod: 'Credit Card'
      },
      {
        invoice: 'INV005',
        paymentStatus: 'Paid',
        totalAmount: '$550.00',
        paymentMethod: 'PayPal'
      },
      {
        invoice: 'INV006',
        paymentStatus: 'Pending',
        totalAmount: '$200.00',
        paymentMethod: 'Bank Transfer'
      },
      {
        invoice: 'INV007',
        paymentStatus: 'Unpaid',
        totalAmount: '$300.00',
        paymentMethod: 'Credit Card'
      }
    ]
  }
};

export default meta;
type Story = StoryObj<TableComponent>;

export const Default: Story = {
  render: (args) => ({
    props: args,
    template: `
      <app-table>
        <app-table-caption>A list of your recent invoices.</app-table-caption>
        <app-table-header>
          <app-table-row>
            <app-table-head class="w-[100px]">Invoice</app-table-head>
            <app-table-head>Status</app-table-head>
            <app-table-head>Method</app-table-head>
            <app-table-head class="text-right">Amount</app-table-head>
          </app-table-row>
        </app-table-header>
        <app-table-body>
          @for (invoice of invoices; track invoice.invoice) {
            <app-table-row>
              <app-table-cell class="font-medium">{{invoice.invoice}}</app-table-cell>
              <app-table-cell>{{invoice.paymentStatus}}</app-table-cell>
              <app-table-cell>{{invoice.paymentMethod}}</app-table-cell>
              <app-table-cell class="text-right">{{invoice.totalAmount}}</app-table-cell>
            </app-table-row>
          }
        </app-table-body>
        <app-table-footer>
          <app-table-row>
            <app-table-cell colspan="3">Total</app-table-cell>
            <app-table-cell class="text-right">$2,500.00</app-table-cell>
          </app-table-row>
        </app-table-footer>
      </app-table>
    `
  })
};
